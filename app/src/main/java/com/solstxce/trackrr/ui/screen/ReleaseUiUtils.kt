package com.solstxce.trackrr.ui.screen

import com.solstxce.trackrr.data.model.GithubRelease
import java.time.Instant
import java.time.ZoneId

fun extractImageUrls(body: String?): List<String> {
    if (body.isNullOrBlank()) return emptyList()

    val htmlImageRegex = Regex("""<img[^>]*src=[\"']([^\"']+)[\"'][^>]*>""", RegexOption.IGNORE_CASE)
    val markdownImageRegex = Regex("""!\[[^\]]*]\((https?://[^)\s]+)\)""")

    val htmlUrls = htmlImageRegex.findAll(body).map { it.groupValues[1] }
    val markdownUrls = markdownImageRegex.findAll(body).map { it.groupValues[1] }

    return (htmlUrls + markdownUrls)
        .map { it.trim().trim('"', '\'', ' ') }
        .map(::normalizeImageUrl)
        .filter { it.startsWith("http") }
        .distinct()
        .toList()
}

fun cleanReleaseBodyForMarkdown(body: String?): String {
    if (body.isNullOrBlank()) return ""

    val htmlImageRegex = Regex("""<img[^>]*src=[\"'][^\"']+[\"'][^>]*>""", RegexOption.IGNORE_CASE)
    val markdownImageRegex = Regex("""!\[[^\]]*]\(https?://[^)\s]+\)""")

    return body
        .replace(htmlImageRegex, " ")
        .replace(markdownImageRegex, " ")
        .replace("\r", "")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

fun releaseSummary(body: String?): String {
    return cleanReleaseBodyForMarkdown(body)
        .replace(Regex("<[^>]*>"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

fun formatPublishedAt(value: String): String {
    return runCatching {
        Instant.parse(value)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()
    }.getOrDefault(value)
}

fun parsePublishedInstant(value: String?): Instant? {
    if (value.isNullOrBlank()) return null
    return runCatching { Instant.parse(value) }.getOrNull()
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}

fun matchesSearchQuery(release: GithubRelease, query: String): Boolean {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) return true

    val haystack = buildString {
        append(release.name.orEmpty())
        append(" ")
        append(release.tagName)
        append(" ")
        append(release.body.orEmpty())
        append(" ")
        release.assets.forEach {
            append(it.name)
            append(" ")
        }
    }.lowercase()

    return haystack.contains(normalized)
}

fun extractVersionFromZipDownloadUrl(release: GithubRelease): String? {
    val zipAsset = release.assets
        .filter {
            it.browserDownloadUrl.contains(".zip", ignoreCase = true) ||
                it.name.endsWith(".zip", ignoreCase = true)
        }
        .maxByOrNull { it.size }
        ?: return null

    val fileName = zipAsset.browserDownloadUrl
        .substringAfterLast("/")
        .substringBefore("?")
        .ifBlank { zipAsset.name }
        .removeSuffix(".zip")

    val semanticVersion = Regex("""(?i)(?:v|version)[-_\s]?(\d+(?:\.\d+){0,2})""")
        .find(fileName)
        ?.groupValues
        ?.getOrNull(1)
    if (!semanticVersion.isNullOrBlank()) return semanticVersion

    val androidVersion = Regex("""(?i)(?:android|a)[-_\s]?(\d{1,2})""")
        .find(fileName)
        ?.groupValues
        ?.getOrNull(1)
    if (!androidVersion.isNullOrBlank()) return androidVersion

    val numericCandidates = Regex("""(?<!\d)(\d{1,2})(?!\d)""")
        .findAll(fileName)
        .map { it.groupValues[1] }
        .toList()

    val likelyAndroidVersion = numericCandidates
        .mapNotNull { it.toIntOrNull() }
        .firstOrNull { it in 8..20 }

    return likelyAndroidVersion?.toString()
}

private fun normalizeImageUrl(raw: String): String {
    if (
        raw.contains("github.com/user-attachments/assets/") &&
        !raw.contains("?raw=")
    ) {
        return "$raw?raw=1"
    }
    return raw
}
