package com.solstxce.trackrr.data.downloader

enum class DownloadPhase {
    IDLE,
    PREPARING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED
}

enum class VerificationPhase {
    NONE,
    PENDING,
    VERIFIED,
    FAILED
}

data class AssetDownloadState(
    val phase: DownloadPhase = DownloadPhase.IDLE,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val progress: Float = 0f,
    val threadCount: Int = 1,
    val resumable: Boolean = false,
    val localFilePath: String? = null,
    val computedSha256: String? = null,
    val expectedSha256: String? = null,
    val verificationPhase: VerificationPhase = VerificationPhase.NONE,
    val message: String? = null
)
