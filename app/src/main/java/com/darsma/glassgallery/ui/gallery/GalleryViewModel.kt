package com.darsma.glassgallery.ui.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darsma.glassgallery.data.FavoritesStore
import com.darsma.glassgallery.data.MediaStoreVideoSource
import com.darsma.glassgallery.data.SortOrder
import com.darsma.glassgallery.data.Video
import com.darsma.glassgallery.data.sortedBy
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

    private val videoSource    = MediaStoreVideoSource(application)
    private val favoritesStore = FavoritesStore(application)

    /** Unsorted master list straight from MediaStore. */
    private var rawVideos: List<Video> = emptyList()

    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.PermissionRequired)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _sortOrder = MutableStateFlow(favoritesStore.loadSortOrder())
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _favorites = MutableStateFlow(favoritesStore.loadFavorites())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()

    /** When true, only favorited videos are shown. */
    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly: StateFlow<Boolean> = _showFavoritesOnly.asStateFlow()

    /** Free-text search query applied to video titles. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentVideo = MutableStateFlow<Video?>(null)
    val currentVideo: StateFlow<Video?> = _currentVideo.asStateFlow()

    private val _currentProgress = MutableStateFlow(0f)
    val currentProgress: StateFlow<Float> = _currentProgress.asStateFlow()

    private val _currentIsPlaying = MutableStateFlow(false)
    val currentIsPlaying: StateFlow<Boolean> = _currentIsPlaying.asStateFlow()

    fun onPermissionGranted() { loadVideos() }
    fun onPermissionDenied()  { _uiState.value = GalleryUiState.PermissionRequired }

    fun selectVideo(video: Video) {
        _currentVideo.value     = video
        _currentProgress.value  = 0f
        _currentIsPlaying.value = false
    }

    fun updatePlaybackState(progress: Float, isPlaying: Boolean) {
        _currentProgress.value  = progress
        _currentIsPlaying.value = isPlaying
    }

    // ── Sorting ────────────────────────────────────────────────────────────

    fun setSortOrder(order: SortOrder) {
        if (order == _sortOrder.value) return
        _sortOrder.value = order
        favoritesStore.saveSortOrder(order)
        applyFilters()
    }

    // ── Favorites ──────────────────────────────────────────────────────────

    fun isFavorite(id: Long): Boolean = id in _favorites.value

    fun toggleFavorite(id: Long) {
        val updated = _favorites.value.toMutableSet().apply {
            if (!add(id)) remove(id)
        }
        _favorites.value = updated
        favoritesStore.saveFavorites(updated)
        // Re-filter only matters when the favorites-only filter is active.
        if (_showFavoritesOnly.value) applyFilters()
    }

    fun toggleFavoritesFilter() {
        _showFavoritesOnly.value = !_showFavoritesOnly.value
        applyFilters()
    }

    // ── Search ─────────────────────────────────────────────────────────────

    fun setSearchQuery(query: String) {
        if (query == _searchQuery.value) return
        _searchQuery.value = query
        applyFilters()
    }

    // ── Internals ──────────────────────────────────────────────────────────

    private fun loadVideos() {
        viewModelScope.launch {
            _uiState.value = GalleryUiState.Loading
            runCatching { videoSource.loadVideos() }
                .onSuccess {
                    rawVideos = it
                    applyFilters()
                }
                .onFailure {
                    _uiState.value = GalleryUiState.Error(it.message ?: "Unknown error")
                }
        }
    }

    /** Applies search + favorites filter + current sort, then publishes Success. */
    private fun applyFilters() {
        val query = _searchQuery.value.trim()
        val filtered = rawVideos
            .asSequence()
            .filter { !_showFavoritesOnly.value || it.id in _favorites.value }
            .filter { query.isEmpty() || it.title.contains(query, ignoreCase = true) }
            .toList()
        _uiState.value = GalleryUiState.Success(filtered.sortedBy(_sortOrder.value))
    }
}
