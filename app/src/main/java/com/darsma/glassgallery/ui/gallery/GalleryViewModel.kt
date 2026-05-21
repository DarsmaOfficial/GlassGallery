package com.darsma.glassgallery.ui.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darsma.glassgallery.data.MediaStoreVideoSource
import com.darsma.glassgallery.data.Video
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface GalleryUiState {
    data object Loading            : GalleryUiState
    data object PermissionRequired : GalleryUiState
    data class  Success(val videos: List<Video>) : GalleryUiState
    data class  Error(val message: String)       : GalleryUiState
}

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val videoSource = MediaStoreVideoSource(application)

    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.PermissionRequired)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _currentVideo = MutableStateFlow<Video?>(null)
    val currentVideo: StateFlow<Video?> = _currentVideo.asStateFlow()

    fun onPermissionGranted() { loadVideos() }

    fun onPermissionDenied()  { _uiState.value = GalleryUiState.PermissionRequired }

    fun selectVideo(video: Video) { _currentVideo.value = video }

    private fun loadVideos() {
        viewModelScope.launch {
            _uiState.value = GalleryUiState.Loading
            runCatching { videoSource.loadVideos() }
                .onSuccess { _uiState.value = GalleryUiState.Success(it) }
                .onFailure { _uiState.value = GalleryUiState.Error(it.message ?: "Unknown error") }
        }
    }
}
