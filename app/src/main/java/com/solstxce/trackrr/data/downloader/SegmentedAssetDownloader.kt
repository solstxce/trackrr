package com.solstxce.trackrr.data.downloader

import android.content.Context
import android.os.Environment
import com.solstxce.trackrr.data.model.GithubAsset
import com.solstxce.trackrr.data.model.GithubRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext
import kotlin.math.ceil

class SegmentedAssetDownloader(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) {
    private val appContext = context.applicationContext
    private val stateFileMutex = Mutex()

    suspend fun resolveExpectedSha256(release: GithubRelease, asset: GithubAsset): String? {
        parseSha256FromText(release.body, asset.name)?.let { return it }

        val checksumAsset = release.assets.firstOrNull { isChecksumAsset(it.name) } ?: return null
        val checksumText = fetchText(checksumAsset.browserDownloadUrl) ?: return null

        return parseSha256FromText(checksumText, asset.name)
            ?: parseAnySha256(checksumText)
    }

    suspend fun downloadAsset(
        asset: GithubAsset,
        expectedSha256: String?,
        maxThreads: Int = 4,
        onProgress: (downloadedBytes: Long, totalBytes: Long, threadCount: Int, resumable: Boolean) -> Unit
    ): DownloadResult = withContext(ioDispatcher) {
        val targetFile = targetFileForAsset(asset)
        val tempFile = tempFileForAsset(asset)
        val stateFile = stateFileForAsset(asset)

        val remoteInfo = inspectRemote(asset.browserDownloadUrl, asset.size)
        val totalBytes = remoteInfo.totalBytes

        if (targetFile.exists() && targetFile.length() == totalBytes && totalBytes > 0L) {
            val computed = computeSha256(targetFile)
            return@withContext DownloadResult(
                file = targetFile,
                computedSha256 = computed,
                expectedSha256 = expectedSha256,
                verified = expectedSha256?.equals(computed, ignoreCase = true),
                totalBytes = totalBytes,
                threadCount = 1,
                resumable = remoteInfo.supportsRanges
            )
        }

        val canResume = remoteInfo.supportsRanges && totalBytes > 0L
        if (!canResume) {
            tempFile.delete()
            stateFile.delete()
        }

        val selectedThreadCount = when {
            !canResume -> 1
            else -> chooseThreadCount(totalBytes, maxThreads)
        }

        val resumeState = loadResumeState(stateFile)
        val segments = if (
            resumeState != null &&
            resumeState.totalBytes == totalBytes &&
            resumeState.threadCount == selectedThreadCount
        ) {
            resumeState.segments.toMutableList()
        } else {
            createSegments(totalBytes, selectedThreadCount)
        }

        if (!tempFile.exists()) {
            tempFile.parentFile?.mkdirs()
            tempFile.createNewFile()
        }

        RandomAccessFile(tempFile, "rw").use { raf ->
            if (totalBytes > 0L) {
                raf.setLength(totalBytes)
            }
        }

        val initialDownloaded = segments.sumOf { it.downloaded.coerceAtMost(it.length()) }
        val downloadedCounter = AtomicLong(initialDownloaded)
        onProgress(initialDownloaded, totalBytes, selectedThreadCount, canResume)

        try {
            if (selectedThreadCount == 1) {
                val segment = segments.first()
                downloadSegment(
                    url = asset.browserDownloadUrl,
                    segment = segment,
                    tempFile = tempFile,
                    totalBytes = totalBytes,
                    selectedThreadCount = 1,
                    downloadedCounter = downloadedCounter,
                    stateFile = stateFile,
                    segments = segments,
                    useRange = canResume,
                    onProgress = onProgress
                )
            } else {
                coroutineScope {
                    segments.map { segment ->
                        async {
                            downloadSegment(
                                url = asset.browserDownloadUrl,
                                segment = segment,
                                tempFile = tempFile,
                                totalBytes = totalBytes,
                                selectedThreadCount = selectedThreadCount,
                                downloadedCounter = downloadedCounter,
                                stateFile = stateFile,
                                segments = segments,
                                useRange = true,
                                onProgress = onProgress
                            )
                        }
                    }.forEach { it.await() }
                }
            }
        } catch (cancelled: CancellationException) {
            saveResumeState(stateFile, totalBytes, selectedThreadCount, segments)
            throw cancelled
        }

        if (targetFile.exists()) {
            targetFile.delete()
        }

        val moved = tempFile.renameTo(targetFile)
        if (!moved) {
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()
        }

        stateFile.delete()

        val computed = computeSha256(targetFile)

        DownloadResult(
            file = targetFile,
            computedSha256 = computed,
            expectedSha256 = expectedSha256,
            verified = expectedSha256?.equals(computed, ignoreCase = true),
            totalBytes = totalBytes,
            threadCount = selectedThreadCount,
            resumable = canResume
        )
    }

    private suspend fun downloadSegment(
        url: String,
        segment: Segment,
        tempFile: File,
        totalBytes: Long,
        selectedThreadCount: Int,
        downloadedCounter: AtomicLong,
        stateFile: File,
        segments: List<Segment>,
        useRange: Boolean,
        onProgress: (downloadedBytes: Long, totalBytes: Long, threadCount: Int, resumable: Boolean) -> Unit
    ) {
        val remaining = segment.length() - segment.downloaded
        if (remaining <= 0L) return

        val requestStart = segment.start + segment.downloaded

        val requestBuilder = Request.Builder()
            .url(url)
            .get()

        if (useRange) {
            requestBuilder.header("Range", "bytes=$requestStart-${segment.end}")
        }

        val request = requestBuilder.build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Download failed with HTTP ${response.code}")
            }

            if (useRange && requestStart > 0L && response.code != 206) {
                throw IllegalStateException("Server does not support resumable range requests")
            }

            val body = response.body ?: throw IllegalStateException("Missing response body")
            body.byteStream().use { input ->
                RandomAccessFile(tempFile, "rw").use { raf ->
                    raf.seek(requestStart)

                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read = input.read(buffer)
                    var bytesSincePersist = 0L
                    var bytesSinceUiUpdate = 0L

                    while (read >= 0) {
                        coroutineContext.ensureActive()

                        if (read > 0) {
                            raf.write(buffer, 0, read)
                            segment.downloaded += read.toLong()
                            val downloadedNow = downloadedCounter.addAndGet(read.toLong())

                            bytesSincePersist += read
                            bytesSinceUiUpdate += read

                            if (bytesSinceUiUpdate >= 128 * 1024) {
                                onProgress(downloadedNow, totalBytes, selectedThreadCount, useRange)
                                bytesSinceUiUpdate = 0L
                            }

                            if (bytesSincePersist >= 256 * 1024) {
                                saveResumeState(stateFile, totalBytes, selectedThreadCount, segments)
                                bytesSincePersist = 0L
                            }
                        }

                        read = input.read(buffer)
                    }
                }
            }
        }

        saveResumeState(stateFile, totalBytes, selectedThreadCount, segments)
        onProgress(downloadedCounter.get(), totalBytes, selectedThreadCount, useRange)
    }

    private suspend fun inspectRemote(url: String, fallbackSize: Long): RemoteInfo = withContext(ioDispatcher) {
        val headRequest = Request.Builder()
            .url(url)
            .head()
            .build()

        runCatching {
            httpClient.newCall(headRequest).execute().use { response ->
                val headerLength = response.header("Content-Length")?.toLongOrNull()
                val totalBytes = if ((headerLength ?: 0L) > 0L) {
                    headerLength!!
                } else {
                    fallbackSize
                }

                val supportsRanges = response.header("Accept-Ranges")
                    ?.contains("bytes", ignoreCase = true)
                    ?: false

                if (response.isSuccessful && totalBytes > 0L) {
                    return@withContext RemoteInfo(totalBytes = totalBytes, supportsRanges = supportsRanges)
                }
            }
        }

        val probeRequest = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .get()
            .build()

        httpClient.newCall(probeRequest).execute().use { response ->
            val contentRange = response.header("Content-Range")
            val totalFromRange = contentRange
                ?.substringAfterLast('/')
                ?.toLongOrNull()

            val totalBytes = when {
                (totalFromRange ?: 0L) > 0L -> totalFromRange!!
                fallbackSize > 0L -> fallbackSize
                else -> response.header("Content-Length")?.toLongOrNull() ?: 0L
            }

            RemoteInfo(
                totalBytes = totalBytes,
                supportsRanges = response.code == 206
            )
        }
    }

    private fun createSegments(totalBytes: Long, threadCount: Int): List<Segment> {
        if (threadCount <= 1 || totalBytes <= 0L) {
            return listOf(Segment(0L, (totalBytes - 1L).coerceAtLeast(0L), 0L))
        }

        val chunkSize = ceil(totalBytes.toDouble() / threadCount.toDouble()).toLong()
        return (0 until threadCount).map { index ->
            val start = index * chunkSize
            val end = minOf(totalBytes - 1L, start + chunkSize - 1L)
            Segment(start = start, end = end, downloaded = 0L)
        }
    }

    private fun chooseThreadCount(totalBytes: Long, maxThreads: Int): Int {
        if (totalBytes < 8L * 1024L * 1024L) return 1
        if (totalBytes < 64L * 1024L * 1024L) return minOf(2, maxThreads)
        return minOf(4, maxThreads)
    }

    private fun targetFileForAsset(asset: GithubAsset): File {
        val dir = ensureDownloadDirectory()
        return File(dir, sanitizeFileName(asset.name))
    }

    private fun tempFileForAsset(asset: GithubAsset): File {
        val dir = ensureDownloadDirectory()
        return File(dir, "${asset.id}.${sanitizeFileName(asset.name)}.part")
    }

    private fun stateFileForAsset(asset: GithubAsset): File {
        val dir = ensureDownloadDirectory()
        return File(dir, "${asset.id}.${sanitizeFileName(asset.name)}.resume")
    }

    private fun ensureDownloadDirectory(): File {
        val base = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: appContext.filesDir
        return File(base, "trackrr-downloads").apply { mkdirs() }
    }

    private fun sanitizeFileName(fileName: String): String {
        return fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read = input.read(buffer)
            while (read >= 0) {
                if (read > 0) digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun saveResumeState(
        stateFile: File,
        totalBytes: Long,
        threadCount: Int,
        segments: List<Segment>
    ) {
        stateFileMutex.withLock {
            val props = Properties().apply {
                put("totalBytes", totalBytes.toString())
                put("threadCount", threadCount.toString())
                put("segmentCount", segments.size.toString())
                segments.forEachIndexed { index, segment ->
                    put("segment.$index.start", segment.start.toString())
                    put("segment.$index.end", segment.end.toString())
                    put("segment.$index.downloaded", segment.downloaded.toString())
                }
            }

            stateFile.outputStream().use { output ->
                props.store(output, null)
            }
        }
    }

    private fun loadResumeState(stateFile: File): ResumeState? {
        if (!stateFile.exists()) return null

        return runCatching {
            val props = Properties()
            stateFile.inputStream().use { props.load(it) }

            val totalBytes = props.getProperty("totalBytes")?.toLongOrNull() ?: return null
            val threadCount = props.getProperty("threadCount")?.toIntOrNull() ?: return null
            val segmentCount = props.getProperty("segmentCount")?.toIntOrNull() ?: return null

            val segments = (0 until segmentCount).map { index ->
                val start = props.getProperty("segment.$index.start")?.toLongOrNull() ?: return null
                val end = props.getProperty("segment.$index.end")?.toLongOrNull() ?: return null
                val downloaded = props.getProperty("segment.$index.downloaded")?.toLongOrNull() ?: return null
                Segment(start = start, end = end, downloaded = downloaded)
            }

            ResumeState(totalBytes = totalBytes, threadCount = threadCount, segments = segments)
        }.getOrNull()
    }

    private suspend fun fetchText(url: String): String? = withContext(ioDispatcher) {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()?.take(500_000)
            }
        }.getOrNull()
    }

    private fun isChecksumAsset(name: String): Boolean {
        val lower = name.lowercase()
        return lower.contains("sha256") ||
            lower.endsWith(".sha256") ||
            lower.endsWith(".sha256sum") ||
            lower.contains("checksum")
    }

    private fun parseSha256FromText(text: String?, targetName: String): String? {
        if (text.isNullOrBlank()) return null

        val hashRegex = Regex("""\b([a-fA-F0-9]{64})\b""")
        val targetLower = targetName.lowercase()

        for (line in text.lineSequence()) {
            val match = hashRegex.find(line) ?: continue
            if (line.lowercase().contains(targetLower)) {
                return match.groupValues[1].lowercase()
            }
        }

        return null
    }

    private fun parseAnySha256(text: String): String? {
        return Regex("""\b([a-fA-F0-9]{64})\b""")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.lowercase()
    }

    private data class RemoteInfo(
        val totalBytes: Long,
        val supportsRanges: Boolean
    )

    private data class Segment(
        val start: Long,
        val end: Long,
        var downloaded: Long
    ) {
        fun length(): Long = (end - start + 1L).coerceAtLeast(0L)
    }

    private data class ResumeState(
        val totalBytes: Long,
        val threadCount: Int,
        val segments: List<Segment>
    )
}

data class DownloadResult(
    val file: File,
    val computedSha256: String,
    val expectedSha256: String?,
    val verified: Boolean?,
    val totalBytes: Long,
    val threadCount: Int,
    val resumable: Boolean
)
