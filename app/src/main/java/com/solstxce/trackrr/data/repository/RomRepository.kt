package com.solstxce.trackrr.data.repository

import com.solstxce.trackrr.data.model.GithubRelease
import com.solstxce.trackrr.data.network.GithubApiService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RomRepository {
    private val apiService: GithubApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GithubApiService::class.java)
    }

    suspend fun getReleases(owner: String = "himanshuksr0007", repo: String = "OTA-Server"): List<GithubRelease> {
        return try {
            apiService
                .getReleases(owner, repo)
                .filter { !it.draft }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
