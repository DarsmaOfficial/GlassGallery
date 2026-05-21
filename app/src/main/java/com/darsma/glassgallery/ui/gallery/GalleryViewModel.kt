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

    // 0f–1f playback progress — updated by PlayerScreen via callback
    private val _currentProgress = MutableStateFlow(0f)
    val currentProgress: StateFlow<Float> = _currentProgress.asStateFlow()

    private val _currentIsPlaying = MutableStateFlow(false)
    val currentIsPlaying: StateFlow<Boolean> = _currentIsPlaying.asStateFlow()

    fun onPermissionGranted() { loadVideos() }
    fun onPermissionDenied()  { _uiState.value = GalleryUiState.PermissionRequired }

    fun selectVideo(video: Video) {
        _currentVideo.value    = video
        _currentProgress.value = 0f
        _currentIsPlaying.value = false
    }

    fun updatePlaybackState(progress: Float, isPlaying: Boolean) {
        _currentProgress.value  = progress
        _currentIsPlaying.value = isPlaying
    }

    private fun loadVideos() {
        viewModelScope.launch {
            _uiState.value = GalleryUiState.Loading
            runCatching { videoSource.loadVideos() }
                .onSuccess { _uiState.value = GalleryUiState.Success(it) }
                .onFailure { _uiState.value = GalleryUiState.Error(it.message ?: "Unknown error") }
        }
    }
}
