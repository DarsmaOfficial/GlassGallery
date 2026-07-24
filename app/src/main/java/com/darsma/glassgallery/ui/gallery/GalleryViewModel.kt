package com.darsma.glassgallery.ui.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darsma.glassgallery.data.FavoritesStore
import com.darsma.glassgallery.data.ImageSearchIndexer
import com.darsma.glassgallery.data.ImageSearchMetadata
import com.darsma.glassgallery.data.MediaStoreVideoSource
import com.darsma.glassgallery.data.SortOrder
import com.darsma.glassgallery.data.Video
import com.darsma.glassgallery.data.imageSearchCacheKey
import com.darsma.glassgallery.data.sortedBy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import java.util.PriorityQueue

/** Which media kinds the grid is currently showing. */
enum class MediaFilter { ALL, VIDEOS, PHOTOS, ALBUMS }

/** One folder card on the Albums tab. */
data class Album(val name: String, val cover: Video, val count: Int)

/** Pre-normalized, immutable entry used by the live search worker. */
private data class SearchEntry(
    val media: Video,
    val title: String,
    val album: String,
    val visionText: String,
    val labels: String,
    val labelValues: List<String>,
    val titleWords: List<String>,
)

private data class RankedSearchResult(
    val media: Video,
    val score: Int,
    val titleKey: String,
)

sealed interface GalleryUiState {
    data object Loading            : GalleryUiState
    data object PermissionRequired : GalleryUiState
    data class  Success(val videos: List<Video>) : GalleryUiState
    data class  Error(val message: String)       : GalleryUiState
}

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val videoSource    = MediaStoreVideoSource(application)
    private val favoritesStore = FavoritesStore(application)
    private val imageSearchIndexer = ImageSearchIndexer(application)

    /** Unsorted master list straight from MediaStore. */
    private var rawVideos: List<Video> = emptyList()

    /** Immutable normalized index, refreshed as on-device photo metadata arrives. */
    private var searchIndex: List<SearchEntry> = emptyList()
    private val indexedImages = mutableMapOf<String, ImageSearchMetadata>()
    private var searchJob: Job? = null
    private var imageIndexJob: Job? = null

    private val _uiState = MutableStateFlow<GalleryUiState>(GalleryUiState.PermissionRequired)
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _sortOrder = MutableStateFlow(favoritesStore.loadSortOrder())
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _favorites = MutableStateFlow(favoritesStore.loadFavorites())
    val favorites: StateFlow<Set<Long>> = _favorites.asStateFlow()
    private val favoritesSaveMutex = Mutex()
    private val favoritesStateLock = Any()

    /** Remembered pinch-to-resize density for every top-level tab. */
    private val _gridColumns = MutableStateFlow(
        MediaFilter.entries.associateWith { filter ->
            favoritesStore.loadGridColumns(filter.name, defaultColumns(filter))
        }
    )
    val gridColumns: StateFlow<Map<MediaFilter, Int>> = _gridColumns.asStateFlow()

    /** When true, only favorited videos are shown. */
    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly: StateFlow<Boolean> = _showFavoritesOnly.asStateFlow()

    /** Free-text search over titles, albums, OCR text and on-device image labels. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Global ranked matches used by the full-screen Dynamic Island search. */
    private val _searchResults = MutableStateFlow<List<Video>>(emptyList())
    val searchResults: StateFlow<List<Video>> = _searchResults.asStateFlow()

    /** True only while the complete relevance pass is still refining results. */
    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    /** All / Videos / Photos / Albums segmented filter. */
    private val _mediaFilter = MutableStateFlow(MediaFilter.ALL)
    val mediaFilter: StateFlow<MediaFilter> = _mediaFilter.asStateFlow()

    /** Folder cards for the Albums tab, biggest first. */
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums.asStateFlow()

    /** Non-null while browsing inside one album. */
    private val _openAlbum = MutableStateFlow<String?>(null)
    val openAlbum: StateFlow<String?> = _openAlbum.asStateFlow()

    /** Multi-select mode: ids currently selected in the grid. */
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds.asStateFlow()

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
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { favoritesStore.saveSortOrder(order) }
        }
        applyFilters()
    }

    // ── Favorites ──────────────────────────────────────────────────────────

    fun isFavorite(id: Long): Boolean = id in _favorites.value

    fun toggleFavorite(id: Long) {
        if (id <= 0L) return

        // The complete interaction is exception-contained. Even malformed
        // legacy preferences or a vendor-specific storage failure can no
        // longer terminate the activity when a heart is tapped.
        runCatching {
            // Copy-on-write makes the state immutable to Compose. Synchronising
            // the tiny in-memory flip also makes rapid taps deterministic.
            val updated = synchronized(favoritesStateLock) {
                _favorites.value.toMutableSet().apply {
                    if (!add(id)) remove(id)
                }.toSet().also { _favorites.value = it }
            }

            // Re-filter only matters when the favorites-only filter is active.
            if (_showFavoritesOnly.value) runCatching { applyFilters() }
            persistFavorites(updated)
        }
    }

    fun toggleFavoritesFilter() {
        runCatching {
            _showFavoritesOnly.value = !_showFavoritesOnly.value
            applyFilters()
        }
    }

    // ── Search ─────────────────────────────────────────────────────────────

    fun setSearchQuery(query: String) {
        val safeQuery = query.take(MAX_SEARCH_QUERY_LENGTH)
        if (safeQuery == _searchQuery.value) return
        _searchQuery.value = safeQuery
        updateSearchResults(safeQuery)
    }

    // ── Media type filter ──────────────────────────────────────────────────

    fun setMediaFilter(filter: MediaFilter) {
        if (filter == _mediaFilter.value) return
        _mediaFilter.value = filter
        if (filter != MediaFilter.ALBUMS) _openAlbum.value = null
        applyFilters()
    }

    fun openAlbum(name: String?) {
        _openAlbum.value = name
        applyFilters()
    }

    // ── Grid density ───────────────────────────────────────────────────────

    fun setGridColumns(filter: MediaFilter, columns: Int) {
        val safeColumns = columns.coerceIn(MIN_GRID_COLUMNS, MAX_GRID_COLUMNS)
        if (_gridColumns.value[filter] == safeColumns) return
        _gridColumns.value = _gridColumns.value.toMutableMap().apply {
            this[filter] = safeColumns
        }.toMap()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { favoritesStore.saveGridColumns(filter.name, safeColumns) }
        }
    }

    fun adjustGridColumns(filter: MediaFilter, delta: Int) {
        val current = _gridColumns.value[filter] ?: defaultColumns(filter)
        setGridColumns(filter, current + delta)
    }

    // ── Selection ──────────────────────────────────────────────────────────

    fun toggleSelection(id: Long) {
        _selectedIds.value =
            if (id in _selectedIds.value) _selectedIds.value - id
            else _selectedIds.value + id
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /** Content uris of everything currently selected, for share/delete. */
    fun selectedMedia(): List<Video> =
        rawVideos.filter { it.id in _selectedIds.value }

    /** Mark everything selected as a favorite (never un-favorites). */
    fun favoriteSelected() {
        if (_selectedIds.value.isEmpty()) return
        runCatching {
            val updated = synchronized(favoritesStateLock) {
                (_favorites.value + _selectedIds.value)
                    .filter { it > 0L }
                    .toSet()
                    .also { _favorites.value = it }
            }
            if (_showFavoritesOnly.value) runCatching { applyFilters() }
            persistFavorites(updated)
        }
    }

    /** Prune deleted items from the in-memory list — no full reload needed. */
    fun removeFromList(ids: Set<Long>) {
        rawVideos = rawVideos.filterNot { it.id in ids }
        val remainingKeys = rawVideos.asSequence()
            .filterNot { it.isVideo }
            .map(Video::imageSearchCacheKey)
            .toSet()
        indexedImages.keys.retainAll(remainingKeys)
        searchIndex = searchIndex.filterNot { it.media.id in ids }
        if (_currentVideo.value?.id in ids) _currentVideo.value = null
        _selectedIds.value = emptySet()
        applyFilters()
        refreshCurrentSearch()
    }

    private fun persistFavorites(snapshot: Set<Long>) {
        runCatching {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    favoritesSaveMutex.withLock {
                        // Use the newest state once this queued write reaches the
                        // lock; an older tap can never overwrite a newer tap.
                        val newest = synchronized(favoritesStateLock) { _favorites.value.toSet() }
                        favoritesStore.saveFavorites(if (newest == snapshot) snapshot else newest)
                    }
                }
            }
        }
    }

    private fun defaultColumns(filter: MediaFilter): Int = when (filter) {
        MediaFilter.PHOTOS -> 3
        else               -> 2
    }

    // ── Internals ──────────────────────────────────────────────────────────

    /** Public re-scan hook (e.g. after the editor saves a new copy). */
    fun reload() = loadVideos()

    private fun loadVideos() {
        imageIndexJob?.cancel()
        viewModelScope.launch {
            _uiState.value = GalleryUiState.Loading
            try {
                val media = videoSource.loadAllMedia()
                val cachedImages = imageSearchIndexer.loadCached(media)
                val index = withContext(Dispatchers.Default) {
                    buildSearchIndex(media, cachedImages)
                }
                rawVideos = media
                indexedImages.clear()
                indexedImages.putAll(cachedImages)
                searchIndex = index
                applyFilters()
                refreshCurrentSearch()
                startImageIndexing(media, cachedImages.keys)
            } catch (error: Throwable) {
                _uiState.value = GalleryUiState.Error(error.message ?: "Unknown error")
            }
        }
    }

    /** Applies tab + favorites filters and the current sort, then publishes Success. */
    private fun applyFilters() {
        // Rebuild album cards from the raw list every pass — cheap, always fresh.
        _albums.value = rawVideos
            .groupBy { it.bucketName.ifBlank { "Other" } }
            .map { (name, items) ->
                Album(name, items.maxByOrNull { it.dateAdded } ?: items.first(), items.size)
            }
            .sortedByDescending { it.count }

        // Search is a modal surface in v24 and no longer mutates the hidden
        // gallery grid on every keystroke. Keeping the content scene stable is
        // the largest reduction in input jank.
        val filtered = rawVideos
            .asSequence()
            .filter {
                when (_mediaFilter.value) {
                    MediaFilter.ALL    -> true
                    MediaFilter.VIDEOS -> it.isVideo
                    MediaFilter.PHOTOS -> !it.isVideo
                    MediaFilter.ALBUMS ->
                        _openAlbum.value == null ||
                            it.bucketName.ifBlank { "Other" } == _openAlbum.value
                }
            }
            .filter { !_showFavoritesOnly.value || it.id in _favorites.value }
            .toList()

        _uiState.value = GalleryUiState.Success(filtered.sortedBy(_sortOrder.value))
    }

    private fun refreshCurrentSearch() {
        updateSearchResults(_searchQuery.value)
    }

    private fun updateSearchResults(query: String) {
        searchJob?.cancel()

        val phrase = normalizeForSearch(query)
        if (phrase.isBlank()) {
            _searchResults.value = emptyList()
            _searching.value = false
            return
        }

        _searching.value = true
        val terms = searchTerms(phrase)
        val snapshot = searchIndex

        // Publish a lightweight first pass in the same input frame. This makes
        // the first visible results arrive without waiting for Enter or for a
        // full relevance sort. The background pass refines/reorders them.
        _searchResults.value = snapshot.asSequence()
            // Never scan a very large camera roll on the UI thread. The
            // bounded warm pass gives common/recent matches immediately while
            // the complete relevance pass runs on Dispatchers.Default.
            .take(INSTANT_SEARCH_SCAN_LIMIT)
            .filter { entryMatches(it, terms) }
            .take(INSTANT_SEARCH_RESULTS)
            .map { it.media }
            .toList()

        searchJob = viewModelScope.launch {
            try {
                val ranked = withContext(Dispatchers.Default) {
                    rankSearch(snapshot, phrase, terms)
                }
                if (_searchQuery.value == query) {
                    _searchResults.value = ranked
                }
            } finally {
                // A cancelled older key press must never clear the loading
                // state owned by the newest query.
                if (_searchQuery.value == query) _searching.value = false
            }
        }
    }

    private fun startImageIndexing(
        media: List<Video>,
        cachedKeys: Set<String>,
    ) {
        imageIndexJob = viewModelScope.launch {
            val pending = linkedMapOf<String, ImageSearchMetadata>()
            try {
                imageSearchIndexer.indexMissing(media, cachedKeys) { photo, metadata ->
                    pending[photo.imageSearchCacheKey()] = metadata
                    if (pending.size >= IMAGE_INDEX_UPDATE_BATCH_SIZE) {
                        applyIndexedImages(pending.toMap())
                        pending.clear()
                    }
                }
            } finally {
                if (pending.isNotEmpty()) applyIndexedImages(pending.toMap())
            }
        }
    }

    private suspend fun applyIndexedImages(updates: Map<String, ImageSearchMetadata>) {
        while (true) {
            val snapshot = searchIndex
            val updatedIndex = withContext(Dispatchers.Default) {
                snapshot.map { entry ->
                    val metadata =
                        updates[entry.media.imageSearchCacheKey()] ?: return@map entry
                    entry.withImageMetadata(metadata)
                }
            }
            // A delete can replace the index while this batch is normalizing
            // off-thread. Retry against that newer list instead of restoring
            // an item that has just been removed.
            if (searchIndex === snapshot) {
                searchIndex = updatedIndex
                break
            }
        }
        indexedImages.putAll(updates)
        if (_searchQuery.value.isNotBlank()) refreshCurrentSearch()
    }

    private fun buildSearchIndex(
        media: List<Video>,
        imageMetadata: Map<String, ImageSearchMetadata>,
    ): List<SearchEntry> =
        media.map { item ->
            val title = normalizeForSearch(item.title)
            val album = normalizeForSearch(item.bucketName.ifBlank { "Other" })
            val metadata = imageMetadata[item.imageSearchCacheKey()]
            val labelValues = metadata?.labels.orEmpty()
                .map(::normalizeForSearch)
                .filter { it.isNotBlank() }
            SearchEntry(
                media = item,
                title = title,
                album = album,
                visionText = normalizeForSearch(metadata?.text.orEmpty()),
                labels = labelValues.joinToString(" "),
                labelValues = labelValues,
                titleWords = WORD_SEPARATOR.split(title).filter { it.isNotBlank() },
            )
        }

    private fun SearchEntry.withImageMetadata(
        metadata: ImageSearchMetadata,
    ): SearchEntry {
        val labelValues = metadata.labels
            .map(::normalizeForSearch)
            .filter { it.isNotBlank() }
        return copy(
            visionText = normalizeForSearch(metadata.text),
            labels = labelValues.joinToString(" "),
            labelValues = labelValues,
        )
    }

    private suspend fun rankSearch(
        snapshot: List<SearchEntry>,
        phrase: String,
        terms: List<String>,
    ): List<Video> {
        // Keep only the best visible candidates while scanning. A one-letter
        // query can match an entire camera roll; sorting every match used to
        // allocate and compare thousands of objects for each key press.
        val best = PriorityQueue(
            MAX_SEARCH_RESULTS + 1,
            SEARCH_RESULT_ORDER.reversed(),
        )
        snapshot.forEachIndexed { index, entry ->
            // A cancelled query must stop doing CPU work immediately. Sequence
            // operators do not necessarily observe coroutine cancellation while
            // traversing a large camera roll, which allowed stale key presses to
            // compete with the newest one.
            if ((index and 127) == 0) currentCoroutineContext().ensureActive()
            if (entryMatches(entry, terms)) {
                val candidate = RankedSearchResult(
                    media = entry.media,
                    score = searchScore(entry, phrase, terms),
                    titleKey = entry.title,
                )
                if (best.size < MAX_SEARCH_RESULTS) {
                    best += candidate
                } else if (SEARCH_RESULT_ORDER.compare(candidate, best.peek()) < 0) {
                    best.poll()
                    best += candidate
                }
            }
        }
        currentCoroutineContext().ensureActive()
        return best
            .toList()
            .sortedWith(SEARCH_RESULT_ORDER)
            .map { it.media }
    }

    private fun entryMatches(entry: SearchEntry, terms: List<String>): Boolean =
        terms.isNotEmpty() && terms.all { term ->
            term in entry.title ||
                term in entry.album ||
                term in entry.visionText ||
                term in entry.labels
        }

    private fun searchScore(
        entry: SearchEntry,
        phrase: String,
        terms: List<String>,
    ): Int {
        var score = when {
            entry.title == phrase -> 1_000
            entry.title.startsWith(phrase) -> 820
            entry.titleWords.any { it.startsWith(phrase) } -> 720
            phrase in entry.title -> 620
            entry.album == phrase -> 520
            entry.album.startsWith(phrase) -> 440
            phrase in entry.album -> 360
            entry.labelValues.any { it == phrase } -> 340
            entry.labelValues.any { it.startsWith(phrase) } -> 300
            phrase in entry.labels -> 260
            phrase in entry.visionText -> 220
            else -> 0
        }
        terms.forEach { term ->
            if (entry.titleWords.any { it == term }) score += 80
            else if (entry.titleWords.any { it.startsWith(term) }) score += 52
            else if (term in entry.title) score += 30
            if (term in entry.album) score += 18
            if (entry.labelValues.any { it == term }) score += 44
            else if (term in entry.labels) score += 26
            if (term in entry.visionText) score += 20
        }
        return score
    }

    private fun searchTerms(phrase: String): List<String> =
        WORD_SEPARATOR.split(phrase).filter { it.isNotBlank() }

    private fun normalizeForSearch(value: String): String =
        DIACRITICS.replace(
            Normalizer.normalize(value, Normalizer.Form.NFD),
            "",
        ).lowercase(Locale.ROOT).trim()

    override fun onCleared() {
        imageIndexJob?.cancel()
        searchJob?.cancel()
        imageSearchIndexer.close()
        super.onCleared()
    }

    private companion object {
        const val MIN_GRID_COLUMNS = 2
        const val MAX_GRID_COLUMNS = 5
        const val MAX_SEARCH_QUERY_LENGTH = 96
        const val INSTANT_SEARCH_RESULTS = 24
        const val INSTANT_SEARCH_SCAN_LIMIT = 384
        const val MAX_SEARCH_RESULTS = 96
        const val IMAGE_INDEX_UPDATE_BATCH_SIZE = 12

        val WORD_SEPARATOR = Regex("[^\\p{L}\\p{N}]+")
        val DIACRITICS = Regex("\\p{M}+")
        val SEARCH_RESULT_ORDER: Comparator<RankedSearchResult> =
            compareByDescending<RankedSearchResult> { it.score }
                .thenByDescending { it.media.dateAdded }
                .thenBy { it.titleKey }
    }
}
