package com.solstxce.trackrr.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.solstxce.trackrr.data.downloader.AssetDownloadState
import com.solstxce.trackrr.data.downloader.DownloadPhase
import com.solstxce.trackrr.data.downloader.SegmentedAssetDownloader
import com.solstxce.trackrr.data.downloader.VerificationPhase
import com.solstxce.trackrr.data.model.GithubAsset
import com.solstxce.trackrr.data.model.GithubRelease
import com.solstxce.trackrr.data.repository.RomRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class RomViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RomRepository()
    private val downloader = SegmentedAssetDownloader(application)
    private val activeDownloadJobs = mutableMapOf<Long, Job>()

    private val _releases = MutableStateFlow<List<GithubRelease>>(emptyList())
    val releases: StateFlow<List<GithubRelease>> = _releases.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _downloadStates = MutableStateFlow<Map<Long, AssetDownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<Long, AssetDownloadState>> = _downloadStates.asStateFlow()

    init {
        fetchReleases()
    }

    fun refresh() {
        fetchReleases()
    }

    fun fetchReleases(owner: String = "himanshuksr0007", repo: String = "OTA-Server") {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null

            val fetchedReleases = repository.getReleases(owner, repo)
            _releases.value = fetchedReleases

            if (fetchedReleases.isEmpty()) {
                _errorMessage.value = "Unable to load releases. Check connection or repository details."
            }

            _isLoading.value = false
        }
    }

    fun onDownloadAction(release: GithubRelease, asset: GithubAsset) {
        val active = activeDownloadJobs[asset.id]
        if (active?.isActive == true) {
            pauseDownload(asset.id)
            return
        }
        startOrResumeDownload(release, asset)
    }

    fun pauseDownload(assetId: Long) {
        val job = activeDownloadJobs.remove(assetId) ?: return
        viewModelScope.launch {
            job.cancelAndJoin()
        }
        updateDownloadState(assetId) {
            it.copy(
                phase = DownloadPhase.PAUSED,
                message = "Paused. Tap Resume to continue."
            )
        }
    }

    private fun startOrResumeDownload(release: GithubRelease, asset: GithubAsset) {
        if (activeDownloadJobs[asset.id]?.isActive == true) return

        val job = viewModelScope.launch {
            updateDownloadState(asset.id) {
                it.copy(
                    phase = DownloadPhase.PREPARING,
                    verificationPhase = VerificationPhase.PENDING,
                    message = "Preparing download..."
                )
            }

            val expectedSha256 = downloader.resolveExpectedSha256(release, asset)

            updateDownloadState(asset.id) {
                it.copy(
                    expectedSha256 = expectedSha256,
                    verificationPhase = if (expectedSha256 != null) VerificationPhase.PENDING else VerificationPhase.NONE,
                    message = if (expectedSha256 != null) {
                        "Checksum found. Verification will run after download."
                    } else {
                        "No checksum file found. Hash will still be calculated for later verification."
                    }
                )
            }

            runCatching {
                downloader.downloadAsset(
                    asset = asset,
                    expectedSha256 = expectedSha256,
                    onProgress = { downloadedBytes, totalBytes, threadCount, resumable ->
                        updateDownloadState(asset.id) { current ->
                            val progress = if (totalBytes > 0L) {
                                (downloadedBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0).toFloat()
                            } else {
                                0f
                            }

                            current.copy(
                                phase = DownloadPhase.DOWNLOADING,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                progress = progress,
                                threadCount = threadCount,
                                resumable = resumable,
                                message = "${(progress * 100f).roundToInt()}%"
                            )
                        }
                    }
                )
            }.onSuccess { result ->
                val verificationPhase = when (result.verified) {
                    true -> VerificationPhase.VERIFIED
                    false -> VerificationPhase.FAILED
                    null -> VerificationPhase.NONE
                }

                val resultMessage = when (verificationPhase) {
                    VerificationPhase.VERIFIED -> "Download complete. SHA-256 verified."
                    VerificationPhase.FAILED -> "Download complete, but checksum verification failed."
                    VerificationPhase.NONE -> "Download complete. Hash generated for later verification."
                    VerificationPhase.PENDING -> "Download complete."
                }

                updateDownloadState(asset.id) {
                    it.copy(
                        phase = DownloadPhase.COMPLETED,
                        downloadedBytes = result.totalBytes,
                        totalBytes = result.totalBytes,
                        progress = 1f,
                        threadCount = result.threadCount,
                        resumable = result.resumable,
                        localFilePath = result.file.absolutePath,
                        computedSha256 = result.computedSha256,
                        expectedSha256 = result.expectedSha256,
                        verificationPhase = verificationPhase,
                        message = resultMessage
                    )
                }
            }.onFailure { throwable ->
                if (throwable is kotlinx.coroutines.CancellationException) {
                    updateDownloadState(asset.id) {
                        it.copy(
                            phase = DownloadPhase.PAUSED,
                            message = "Paused. Tap Resume to continue."
                        )
                    }
                } else {
                    updateDownloadState(asset.id) {
                        it.copy(
                            phase = DownloadPhase.FAILED,
                            message = throwable.message ?: "Download failed."
                        )
                    }
                }
            }

            activeDownloadJobs.remove(asset.id)
        }

        activeDownloadJobs[asset.id] = job
    }

    private fun updateDownloadState(assetId: Long, transform: (AssetDownloadState) -> AssetDownloadState) {
        _downloadStates.update { current ->
            val previous = current[assetId] ?: AssetDownloadState()
            current + (assetId to transform(previous))
        }
    }

    override fun onCleared() {
        super.onCleared()
        activeDownloadJobs.values.forEach { it.cancel() }
        activeDownloadJobs.clear()
    }
}
