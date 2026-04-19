package com.solstxce.trackrr.data.model

import com.google.gson.annotations.SerializedName

data class GithubRelease(
    val id: Long,
    val name: String?,
    @SerializedName("tag_name")
    val tagName: String,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val body: String?,
    @SerializedName("published_at")
    val publishedAt: String?,
    @SerializedName("html_url")
    val htmlUrl: String,
    val assets: List<GithubAsset> = emptyList()
)

data class GithubAsset(
    val id: Long,
    val name: String,
    val size: Long,
    @SerializedName("browser_download_url")
    val browserDownloadUrl: String,
    @SerializedName("download_count")
    val downloadCount: Int
)
