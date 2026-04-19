package com.solstxce.trackrr.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.solstxce.trackrr.data.model.GithubRelease
import com.solstxce.trackrr.data.repository.RomRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RomViewModel : ViewModel() {
    private val repository = RomRepository()

    private val _releases = MutableStateFlow<List<GithubRelease>>(emptyList())
    val releases: StateFlow<List<GithubRelease>> = _releases.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

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
}
