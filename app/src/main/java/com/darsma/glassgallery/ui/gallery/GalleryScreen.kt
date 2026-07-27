@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)

package com.darsma.glassgallery.ui.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.app.Activity
import android.content.Intent
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.TransformOrigin
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.darsma.glassgallery.R
import com.darsma.glassgallery.data.SortOrder
import com.darsma.glassgallery.data.Video
import com.darsma.glassgallery.data.formatBytes
import com.darsma.glassgallery.data.toTimeline
import com.darsma.glassgallery.ui.components.AuroraBackground
import com.darsma.glassgallery.ui.components.BouncyIconButton
import com.darsma.glassgallery.ui.components.GlassBackdropHost
import com.darsma.glassgallery.ui.components.GlassSurface
import com.darsma.glassgallery.ui.components.LiquidTabBar
import com.darsma.glassgallery.ui.components.liquidGlass
import com.darsma.glassgallery.ui.components.Motion
import com.darsma.glassgallery.ui.components.MiniPlayer
import com.darsma.glassgallery.ui.components.liquidGlassBorder
import com.darsma.glassgallery.ui.components.favoriteBurst
import com.darsma.glassgallery.ui.components.pressBounce
import com.darsma.glassgallery.ui.components.rememberGlassBackdropState
import com.darsma.glassgallery.ui.components.shimmer
import com.darsma.glassgallery.ui.components.specularFlash
import com.darsma.glassgallery.ui.components.touchLens
import com.darsma.glassgallery.ui.components.opticalGlass
import com.darsma.glassgallery.ui.theme.GlassRole
import com.darsma.glassgallery.ui.theme.PillShape
import com.darsma.glassgallery.ui.theme.TabularFigures
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MINI_PLAYER_HEIGHT = 92.dp

private data class TimelineJump(val label: String, val itemIndex: Int)

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    animatedVisibilityScope: AnimatedVisibilityScope,
    sharedTransitionScope: SharedTransitionScope,
    onVideoClick: (Video) -> Unit,
    onMiniPlayerClick: () -> Unit,
    onOpenTrash: () -> Unit,
) {
    val context           = LocalContext.current
    val uiState           by viewModel.uiState.collectAsState()
    val currentVideo      by viewModel.currentVideo.collectAsState()
    val currentProgress   by viewModel.currentProgress.collectAsState()
    val currentIsPlaying  by viewModel.currentIsPlaying.collectAsState()
    val sortOrder         by viewModel.sortOrder.collectAsState()
    val favorites         by viewModel.favorites.collectAsState()
    val favoritesOnly     by viewModel.showFavoritesOnly.collectAsState()
    val mediaFilter       by viewModel.mediaFilter.collectAsState()
    val selectedIds       by viewModel.selectedIds.collectAsState()
    val albums            by viewModel.albums.collectAsState()
    val openAlbum         by viewModel.openAlbum.collectAsState()
    val gridColumnsByTab  by viewModel.gridColumns.collectAsState()
    val selectionMode     = selectedIds.isNotEmpty()
    val haptic            = LocalHapticFeedback.current

    // Selection mode is dismissible with the system back gesture.
    BackHandler(enabled = selectionMode) { viewModel.clearSelection() }
    // Inside an album, back first pops out to the album shelf.
    BackHandler(enabled = !selectionMode && openAlbum != null) { viewModel.openAlbum(null) }

    // System-confirmed bulk delete.
    var pendingDelete by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.removeFromList(pendingDelete)
        pendingDelete = emptySet()
    }
    val hasMiniPlayer = currentVideo != null

    var sortSheetVisible by remember { mutableStateOf(false) }
    var searchExpanded   by remember { mutableStateOf(false) }

    // Search owns Back before album navigation so the liquid surface always
    // collapses cleanly instead of unexpectedly leaving the current context.
    BackHandler(enabled = !selectionMode && searchExpanded) {
        searchExpanded = false
        viewModel.setSearchQuery("")
    }

    val permissions = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES)
    else
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    var hasPermission by remember {
        mutableStateOf(permissions.all {
            context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        })
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result.values.all { it }
        if (hasPermission) viewModel.onPermissionGranted() else viewModel.onPermissionDenied()
    }

    LaunchedEffect(Unit) {
        if (hasPermission) viewModel.onPermissionGranted() else permLauncher.launch(permissions)
    }

    // Hoisted grid state — drives the scroll-aware glass header and scrubber.
    val gridState = rememberLazyGridState()
    val screenScope = rememberCoroutineScope()
    val headerGlass = remember {
        derivedStateOf {
            if (gridState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (gridState.firstVisibleItemScrollOffset / 120f).coerceIn(0f, 1f)
            }
        }
    }
    // Pinch-to-resize is remembered independently for every tab.
    val gridColumns = (gridColumnsByTab[mediaFilter]
        ?: if (mediaFilter == MediaFilter.PHOTOS) 3 else 2).coerceIn(2, 5)

    // ── Sticky timeline chip ────────────────────────────────────────────────
    // Mirrors the grid's item order (headers count as items) so the label at
    // or before the first visible index is the section currently on screen.
    val successVideos = (uiState as? GalleryUiState.Success)?.videos ?: emptyList()
    val dateSortedNow = sortOrder == SortOrder.DATE_NEWEST || sortOrder == SortOrder.DATE_OLDEST
    val inAlbumChip   = mediaFilter == MediaFilter.ALBUMS && openAlbum != null
    val flatLabels    = remember(successVideos, dateSortedNow, inAlbumChip) {
        if (!dateSortedNow) emptyList()
        else buildList<String?> {
            if (inAlbumChip) add(null)  // the album back-chip occupies index 0
            successVideos.toTimeline().forEach { section ->
                add(section.label)
                repeat(section.items.size) { add(null) }
            }
        }
    }
    val stickyLabel by remember(flatLabels) {
        derivedStateOf {
            if (flatLabels.isEmpty()) null
            else {
                var i = gridState.firstVisibleItemIndex.coerceAtMost(flatLabels.lastIndex)
                var found: String? = null
                while (i >= 0) {
                    val l = flatLabels[i]
                    if (l != null) { found = l; break }
                    i--
                }
                found
            }
        }
    }
    val timelineJumps = remember(successVideos, dateSortedNow, inAlbumChip) {
        if (!dateSortedNow) emptyList()
        else buildList {
            var itemIndex = if (inAlbumChip) 1 else 0
            successVideos.toTimeline().forEach { section ->
                add(TimelineJump(section.label, itemIndex))
                itemIndex += 1 + section.items.size
            }
        }
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Search is a full scene transition in v24. The underlying gallery keeps
    // its layout position and dissolves into depth instead of jumping downward.
    val contentHeaderOffset = 78.dp
    val searchScene by animateFloatAsState(
        targetValue = if (searchExpanded) 1f else 0f,
        animationSpec = Motion.spatial(),
        label = "dynamic-island-scene",
    )

    val backdropState = rememberGlassBackdropState()
    GlassBackdropHost(
        state = backdropState,
        modifier = Modifier.fillMaxSize(),
        source = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
        // Keep the gallery alive only while it is visible or completing its
        // departure. Once search owns the screen, the thumbnail grid and its
        // ambient effects leave composition entirely instead of consuming
        // frames behind an opaque island. Re-closing search composes the scene
        // at alpha 0 and lets it travel back in without a visual jump.
        if (!searchExpanded || searchScene < 0.995f) {
            // Every gallery element travels as one scene. Opening search removes
            // the title, grid, scrubber, player, and navigation together, leaving
            // only the Dynamic Island surface above the dark ambient backdrop.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                    alpha = (1f - searchScene * 1.08f).coerceIn(0f, 1f)
                    scaleX = 1f - 0.032f * searchScene
                    scaleY = 1f - 0.032f * searchScene
                    translationY = 18f * searchScene
                    transformOrigin = TransformOrigin(0.5f, 0.18f)
                },
            ) {
                // Drifting ambient colour the glass can "refract". It is part of
                // the removable gallery scene, so it stops while typing.
                AuroraBackground()

                // ── Content layer: scrolls edge-to-edge under the floating chrome ──
                Box(Modifier.fillMaxSize()) {
                    AnimatedContent(
                        targetState    = uiState,
                        contentKey     = { it::class },
                        transitionSpec = {
                            fadeIn(Motion.standard()) togetherWith fadeOut(Motion.snappy())
                        },
                        label    = "gallery-state",
                        modifier = Modifier.fillMaxSize(),
                    ) { state ->
                    when (state) {
                        is GalleryUiState.Loading -> CenterBox {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }

                        is GalleryUiState.PermissionRequired -> CenterBox {
                            Text(
                                text     = stringResource(R.string.permission_rationale),
                                color    = MaterialTheme.colorScheme.onSurface,
                                style    = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(32.dp),
                            )
                        }

                        is GalleryUiState.Error -> CenterBox {
                            Text(
                                text     = state.message,
                                color    = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(32.dp),
                            )
                        }

                        is GalleryUiState.Success -> {
                            val albumShelf = mediaFilter == MediaFilter.ALBUMS && openAlbum == null
                            if (albumShelf) {
                                // Search is now a separate modal scene; typing must not
                                // regroup the hidden album shelf on every input frame.
                                val shelfAlbums = remember(albums, state.videos, favoritesOnly) {
                                    if (!favoritesOnly) albums
                                    else state.videos
                                        .groupBy { it.bucketName.ifBlank { "Other" } }
                                        .map { (name, items) ->
                                            Album(
                                                name = name,
                                                cover = items.maxByOrNull { it.dateAdded } ?: items.first(),
                                                count = items.size,
                                            )
                                        }
                                        .sortedByDescending { it.count }
                                }
                                AlbumShelf(
                                    albums      = shelfAlbums,
                                    topPadding  = topInset + contentHeaderOffset,
                                    bottomPad   = 112.dp + if (hasMiniPlayer) MINI_PLAYER_HEIGHT else 0.dp,
                                    onOpen      = { viewModel.openAlbum(it) },
                                    onOpenTrash = onOpenTrash,
                                )
                            } else if (state.videos.isEmpty()) {
                                CenterBox {
                                    Text(
                                        text     = if (favoritesOnly)
                                            "No favorites yet.\nTap the heart on a video to add one."
                                        else
                                            "No media found.",
                                        color     = Color.White.copy(alpha = 0.55f),
                                        style     = MaterialTheme.typography.bodyLarge,
                                        modifier  = Modifier.padding(32.dp),
                                    )
                                }
                            } else {
                                val gap = when (gridColumns) {
                                    2    -> 10.dp
                                    3    -> 5.dp
                                    else -> 3.dp
                                }
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(gridColumns),
                                    state = gridState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .pinchToResizeGrid { delta ->
                                            val next = (gridColumns + delta).coerceIn(2, 5)
                                            if (next != gridColumns) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                viewModel.setGridColumns(mediaFilter, next)
                                            }
                                        },
                                    contentPadding = PaddingValues(
                                        start = gap,
                                        end = gap,
                                        top = topInset + contentHeaderOffset,
                                        bottom = 112.dp + if (hasMiniPlayer) MINI_PLAYER_HEIGHT else 0.dp,
                                    ),
                                    verticalArrangement    = Arrangement.spacedBy(gap),
                                    horizontalArrangement  = Arrangement.spacedBy(gap),
                                ) {
                                    // Shared card emitter so timeline sections and the
                                    // flat grid render identical tiles.
                                    fun LazyGridScope.mediaItems(list: List<Video>) {
                                        itemsIndexed(
                                            items = list,
                                            key   = { _, video -> video.id },
                                        ) { index, video ->
                                            VideoCard(
                                                video         = video,
                                                index         = index,
                                                isFavorite    = video.id in favorites,
                                                columns       = gridColumns,
                                                selected      = video.id in selectedIds,
                                                selectionMode = selectionMode,
                                                sharedTransitionScope   = sharedTransitionScope,
                                                animatedVisibilityScope = animatedVisibilityScope,
                                                onClick     = {
                                                    if (selectionMode) viewModel.toggleSelection(video.id)
                                                    else onVideoClick(video)
                                                },
                                                onLongPress = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    viewModel.toggleSelection(video.id)
                                                },
                                                onToggleFav = { viewModel.toggleFavorite(video.id) },
                                                modifier    = Modifier.animateItem(
                                                    fadeInSpec    = Motion.standard(),
                                                    placementSpec = Motion.expressive(),
                                                    fadeOutSpec   = Motion.snappy(),
                                                ),
                                            )
                                        }
                                    }

                                    // Inside an album: a full-width back chip leads the grid.
                                    if (mediaFilter == MediaFilter.ALBUMS && openAlbum != null) {
                                        item(key = "album-chip", span = { GridItemSpan(maxLineSpan) }) {
                                            AlbumBackChip(
                                                name     = openAlbum ?: "",
                                                count    = state.videos.size,
                                                onBack   = { viewModel.openAlbum(null) },
                                                modifier = Modifier.animateItem(placementSpec = Motion.expressive()),
                                            )
                                        }
                                    }

                                    val dateSorted =
                                        sortOrder == SortOrder.DATE_NEWEST || sortOrder == SortOrder.DATE_OLDEST
                                    if (dateSorted) {
                                        // Timeline: full-span glass date headers per section.
                                        state.videos.toTimeline().forEach { section ->
                                            item(
                                                key  = "hdr-${section.label}",
                                                span = { GridItemSpan(maxLineSpan) },
                                            ) {
                                                TimelineHeader(
                                                    label    = section.label,
                                                    count    = section.items.size,
                                                    modifier = Modifier.animateItem(placementSpec = Motion.expressive()),
                                                )
                                            }
                                            mediaItems(section.items)
                                        }
                                    } else {
                                        mediaItems(state.videos)
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }
            }
        },
        overlay = {
            if (!searchExpanded || searchScene < 0.995f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = (1f - searchScene * 1.08f).coerceIn(0f, 1f)
                            scaleX = 1f - 0.032f * searchScene
                            scaleY = 1f - 0.032f * searchScene
                            translationY = 18f * searchScene
                            transformOrigin = TransformOrigin(0.5f, 0.18f)
                        },
                ) {

                // ── Floating glass header: transparent at rest, frosts on scroll ──
                val successState = uiState as? GalleryUiState.Success
                val mediaList    = successState?.videos ?: emptyList()
                Box(modifier = Modifier.fillMaxWidth()) {
                    GlassSurface(
                        backdrop = backdropState,
                        role = GlassRole.Chrome,
                        shape = RectangleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = headerGlass.value },
                    ) {
                        // This sizing layer keeps the blur behind the status bar.
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding(),
                        ) {
                            Spacer(Modifier.height(58.dp))
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding(),
                    ) {
                        GalleryHeader(
                            collapse       = headerGlass.value,
                            videoCount     = mediaList.count { it.isVideo },
                            photoCount     = mediaList.count { !it.isVideo },
                            totalSizeBytes = mediaList.sumOf { it.sizeBytes },
                            favoritesOnly  = favoritesOnly,
                            onToggleFavs   = { viewModel.toggleFavoritesFilter() },
                            onOpenSort     = { sortSheetVisible = true },
                        )
                    }
                }

                // Compact sticky context chip: useful, but never competes with media.
                AnimatedVisibility(
                    visible = !searchExpanded && headerGlass.value > (16f / 120f) &&
                        stickyLabel != null &&
                        !(mediaFilter == MediaFilter.ALBUMS && openAlbum == null),
                    enter = slideInVertically(initialOffsetY = { -it }, animationSpec = Motion.expressive()) +
                        fadeIn(Motion.standard()),
                    exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = Motion.snappy()) +
                        fadeOut(Motion.snappy()),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = 12.dp, top = 64.dp),
                ) {
                    AnimatedContent(
                        targetState = stickyLabel ?: "",
                        transitionSpec = {
                            (slideInVertically(initialOffsetY = { it / 3 }, animationSpec = Motion.expressive()) +
                                fadeIn(Motion.standard())) togetherWith
                                (slideOutVertically(targetOffsetY = { -it / 3 }, animationSpec = Motion.snappy()) +
                                    fadeOut(Motion.snappy()))
                        },
                        label = "sticky-date",
                    ) { label ->
                        Box(
                            modifier = Modifier
                                .clip(PillShape)
                                .liquidGlass(alpha = 0.74f)
                                .liquidGlassBorder(PillShape)
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.merge(TabularFigures),
                                color = Color.White.copy(alpha = 0.92f),
                                maxLines = 1,
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = !searchExpanded && timelineJumps.size > 1 &&
                        !(mediaFilter == MediaFilter.ALBUMS && openAlbum == null) &&
                        !selectionMode,
                    enter = fadeIn(Motion.standard()),
                    exit = fadeOut(Motion.snappy()),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(top = topInset + 104.dp, bottom = 118.dp, end = 2.dp),
                ) {
                    TimelineScrubber(
                        jumps = timelineJumps,
                        currentLabel = stickyLabel ?: timelineJumps.firstOrNull()?.label.orEmpty(),
                        onJump = { jump ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            screenScope.launch { gridState.scrollToItem(jump.itemIndex) }
                        },
                    )
                }

                MiniPlayer(
                    video                   = currentVideo,
                    isPlaying               = currentIsPlaying,
                    progress                = currentProgress,
                    onPlayPause             = {},
                    onClick                 = onMiniPlayerClick,
                    sharedTransitionScope   = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    modifier                = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 68.dp),
                )

                // ── Floating liquid-glass tab bar, Apple Photos style ──────────────
                AnimatedVisibility(
                    visible  = !selectionMode,
                    enter    = slideInVertically(initialOffsetY = { it * 2 }, animationSpec = Motion.expressive()) +
                               fadeIn(Motion.standard()),
                    exit     = slideOutVertically(targetOffsetY = { it * 2 }, animationSpec = Motion.snappy()) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 10.dp),
                ) {
                    LiquidTabBar(
                        options       = listOf("All", "Videos", "Photos", "Albums"),
                        icons         = listOf(
                            Icons.Rounded.Apps, Icons.Rounded.PlayArrow,
                            Icons.Rounded.Image, Icons.Rounded.PhotoLibrary,
                        ),
                        selectedIndex = when (mediaFilter) {
                            MediaFilter.ALL    -> 0
                            MediaFilter.VIDEOS -> 1
                            MediaFilter.PHOTOS -> 2
                            MediaFilter.ALBUMS -> 3
                        },
                        onSelect      = { index -> viewModel.setMediaFilter(MediaFilter.entries[index]) },
                        modifier      = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    )
                }

                // ── Selection action bar: the tab bar's liquid sibling ─────────────
                AnimatedVisibility(
                    visible  = selectionMode,
                    enter    = slideInVertically(initialOffsetY = { it * 2 }, animationSpec = Motion.expressive()) +
                               fadeIn(Motion.standard()),
                    exit     = slideOutVertically(targetOffsetY = { it * 2 }, animationSpec = Motion.snappy()) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 10.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(52.dp)
                            .clip(PillShape)
                            .liquidGlass(alpha = 0.62f)
                            .specularFlash(trigger = selectedIds.size)
                            .liquidGlassBorder(PillShape)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AnimatedContent(
                            targetState    = selectedIds.size,
                            transitionSpec = {
                                fadeIn(Motion.snappy()) togetherWith fadeOut(Motion.snappy())
                            },
                            label = "sel-count",
                        ) { count ->
                            Text(
                                text       = "$count",
                                style      = MaterialTheme.typography.labelLarge.merge(TabularFigures),
                                color      = MaterialTheme.colorScheme.primary,
                                modifier   = Modifier.padding(start = 10.dp),
                            )
                        }
                        Text(
                            text     = " selected",
                            style    = MaterialTheme.typography.labelLarge,
                            color    = Color.White.copy(alpha = 0.70f),
                            modifier = Modifier.padding(end = 10.dp),
                        )
                        BouncyIconButton(
                            onClick = {
                                val items = viewModel.selectedMedia()
                                if (items.isNotEmpty()) {
                                    val uris = ArrayList(items.map { it.uri })
                                    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                        type = "*/*"
                                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(intent, "Share media"))
                                }
                            },
                            size = 44.dp,
                        ) {
                            Icon(Icons.Rounded.Share, "Share selected", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        BouncyIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.favoriteSelected()
                            },
                            size = 44.dp,
                        ) {
                            Icon(Icons.Rounded.Favorite, "Favorite selected", tint = Color(0xFFFF5C8A), modifier = Modifier.size(20.dp))
                        }
                        BouncyIconButton(
                            onClick = {
                                val items = viewModel.selectedMedia()
                                if (items.isNotEmpty()) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    pendingDelete = items.map { it.id }.toSet()
                                    // Soft delete: 30-day OS recycle bin instead of permanent erase.
                                    val pi = MediaStore.createTrashRequest(
                                        context.contentResolver, items.map { it.uri }, true
                                    )
                                    deleteLauncher.launch(
                                        IntentSenderRequest.Builder(pi.intentSender).build()
                                    )
                                }
                            },
                            size = 44.dp,
                        ) {
                            Icon(Icons.Rounded.Delete, "Delete selected", tint = Color(0xFFFF7A7A), modifier = Modifier.size(20.dp))
                        }
                        BouncyIconButton(
                            onClick = { viewModel.clearSelection() },
                            size    = 44.dp,
                        ) {
                            Icon(Icons.Rounded.Close, "Exit selection", tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                    }
                }

                // Sort bottom-sheet overlays everything.
                SortSheet(
                    visible   = sortSheetVisible,
                    current   = sortOrder,
                    onSelect  = { order ->
                        viewModel.setSortOrder(order)
                        sortSheetVisible = false
                    },
                    onDismiss = { sortSheetVisible = false },
                )
            }
        }

        SearchOverlayHost(
            viewModel = viewModel,
            active = searchExpanded,
            enabled = !selectionMode && !sortSheetVisible,
            onOpen = {
                sortSheetVisible = false
                searchExpanded = true
            },
            onClose = {
                searchExpanded = false
                viewModel.setSearchQuery("")
            },
            onResultClick = { media ->
                screenScope.launch {
                    searchExpanded = false
                    delay(110L)
                    viewModel.setSearchQuery("")
                    onVideoClick(media)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        },
    )
}

@Composable
private fun SearchOverlayHost(
    viewModel: GalleryViewModel,
    active: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onResultClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Query/result state is intentionally read in this leaf composable. A key
    // press now recomposes only the search island instead of the entire media
    // grid, sticky timeline, MiniPlayer, and bottom navigation.
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val searching by viewModel.searching.collectAsState()

    DynamicIslandSearch(
        active = active,
        enabled = enabled,
        query = query,
        results = results,
        searching = searching,
        onQueryChange = viewModel::setSearchQuery,
        onOpen = onOpen,
        onClose = onClose,
        onResultClick = onResultClick,
        modifier = modifier,
    )
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun GalleryHeader(
    collapse: Float,
    videoCount: Int,
    photoCount: Int,
    totalSizeBytes: Long,
    favoritesOnly: Boolean,
    onToggleFavs: () -> Unit,
    onOpenSort: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFF7F3FF),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                AnimatedContent(
                    targetState = Triple(videoCount, photoCount, totalSizeBytes),
                    transitionSpec = {
                        fadeIn(Motion.standard()) togetherWith fadeOut(Motion.snappy())
                    },
                    label = "gallery-subtitle",
                ) { (videos, photos, size) ->
                    Text(
                        text = when {
                            videos == 0 && photos == 0 ->
                                if (favoritesOnly) "No favorites" else "Your media library"
                            else -> "$videos videos  ·  $photos photos  ·  ${formatBytes(size)}"
                        },
                        style = MaterialTheme.typography.bodyMedium.merge(TabularFigures),
                        color = Color.White.copy(alpha = 0.48f - collapse * 0.08f),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            val favProgress by animateFloatAsState(
                targetValue = if (favoritesOnly) 1f else 0f,
                animationSpec = Motion.standard(),
                label = "fav-filter-tint",
            )
            BouncyIconButton(
                onClick = onToggleFavs,
                size = 38.dp,
                background = Color.White.copy(alpha = 0.075f + 0.12f * favProgress),
            ) {
                AnimatedContent(
                    targetState = favoritesOnly,
                    transitionSpec = {
                        scaleIn(Motion.bouncy()) + fadeIn(Motion.snappy()) togetherWith
                            scaleOut(Motion.snappy()) + fadeOut(Motion.snappy())
                    },
                    label = "fav-filter-icon",
                ) { active ->
                    Icon(
                        imageVector = if (active) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Show favorites only",
                        tint = if (active) Color(0xFFFF7099) else Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            BouncyIconButton(
                onClick = onOpenSort,
                size = 38.dp,
                background = Color.White.copy(alpha = 0.075f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SwapVert,
                    contentDescription = "Sort media",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            // The root-level DynamicIslandSearch owns this exact 38dp slot,
            // allowing the orb to morph without being clipped by the header.
            Spacer(Modifier.size(38.dp))
        }
    }
}

@Composable
private fun VideoCard(
    video: Video,
    index: Int,
    isFavorite: Boolean,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
    onToggleFav: () -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 2,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onLongPress: () -> Unit = {},
) {
    val dense = columns >= 3
    val cardInteraction = remember { MutableInteractionSource() }
    val cardPressed by cardInteraction.collectIsPressedAsState()
    val cardCorner by animateDpAsState(
        targetValue = when {
            cardPressed       -> if (dense) 18.dp else 28.dp
            columns >= 4      -> 8.dp
            dense             -> 12.dp
            else              -> 18.dp
        },
        animationSpec = Motion.expressive(),
        label = "card-corner",
    )
    val cardShape = RoundedCornerShape(cardCorner)
    val selectScale by animateFloatAsState(
        targetValue = if (selected) 0.92f else 1f,
        animationSpec = Motion.expressive(),
        label = "card-select-scale",
    )
    val imageScale by animateFloatAsState(
        targetValue = if (cardPressed) 1.075f else 1f,
        animationSpec = Motion.expressive(),
        label = "card-image-breathe",
    )
    val pressGlow by animateFloatAsState(
        targetValue = if (cardPressed) 1f else 0f,
        animationSpec = Motion.snappy(),
        label = "card-press-glow",
    )
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    var pressPoint by remember { mutableStateOf<Offset?>(null) }
    val pressFraction = pressPoint?.let { point ->
        if (cardSize.width > 0 && cardSize.height > 0) {
            Offset(
                x = (point.x / cardSize.width.toFloat()).coerceIn(0f, 1f),
                y = (point.y / cardSize.height.toFloat()).coerceIn(0f, 1f),
            )
        } else null
    }
    val tiltX by animateFloatAsState(
        targetValue = if (cardPressed) -((pressFraction?.y ?: 0.5f) - 0.5f) * 5.2f else 0f,
        animationSpec = Motion.elastic(),
        label = "card-tilt-x",
    )
    val tiltY by animateFloatAsState(
        targetValue = if (cardPressed) ((pressFraction?.x ?: 0.5f) - 0.5f) * 5.2f else 0f,
        animationSpec = Motion.elastic(),
        label = "card-tilt-y",
    )

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(12) * 24L)
        appeared = true
    }
    val appear by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = Motion.expressive(),
        label = "card-appear",
    )

    // Photos own this grid↔viewer morph. Video transition ownership remains
    // exclusively with the MiniPlayer/player pair.
    val photoMorph = if (!video.isVideo) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState("photo-${video.id}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> Motion.expressive() },
            )
        }
    } else Modifier

    Box(
        modifier = modifier
            .graphicsLayer {
                alpha = appear.coerceIn(0f, 1f)
                translationY = (1f - appear) * 34f - pressGlow * 4.dp.toPx()
                val scale = (0.95f + 0.05f * appear) * selectScale
                scaleX = scale
                scaleY = scale
                rotationX = tiltX
                rotationY = tiltY
                cameraDistance = 28.dp.toPx()
                shadowElevation = pressGlow * 15.dp.toPx()
            }
            .pressBounce(cardInteraction, pressedScale = 0.965f, spec = Motion.standard())
            .aspectRatio(1f)
            .onSizeChanged { cardSize = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressPoint = down.position
                    var pressed = true
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            pressed = false
                        } else {
                            pressPoint = change.position
                            pressed = change.pressed
                        }
                    } while (pressed)
                    pressPoint = null
                }
            }
            .then(photoMorph)
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                interactionSource = cardInteraction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongPress,
            ),
    ) {
        var thumbResolved by remember { mutableStateOf(false) }
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(video.thumbnailUri)
                .crossfade(240)
                .build(),
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            onState = { state ->
                thumbResolved = state is AsyncImagePainter.State.Success ||
                    state is AsyncImagePainter.State.Error
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = imageScale
                    scaleY = imageScale
                },
        )

        // Finger-following optical lens: the highlight and subtle spectrum
        // move with touch position instead of flashing from a fixed corner.
        if (pressGlow > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .touchLens(pressFraction, pressGlow),
            )
        }

        val shimmerAlpha by animateFloatAsState(
            targetValue = if (thumbResolved) 0f else 1f,
            animationSpec = tween(durationMillis = 260),
            label = "shimmer-fade",
        )
        if (shimmerAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = shimmerAlpha }
                    .shimmer(),
            )
        }

        if (!dense || video.isVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.00f to Color.Transparent,
                            0.58f to Color.Transparent,
                            1.00f to Color.Black.copy(alpha = if (dense) 0.46f else 0.74f),
                        )
                    ),
            )
        }

        if (video.isVideo) {
            val playSize = if (dense) 28.dp else 36.dp
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(playSize)
                    .clip(PillShape)
                    .background(Color.Black.copy(alpha = 0.28f))
                    .liquidGlassBorder(PillShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.94f),
                    modifier = Modifier
                        .size(if (dense) 17.dp else 21.dp)
                        .padding(start = 1.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = !selectionMode,
            enter = scaleIn(Motion.bouncy(), initialScale = 0.65f) + fadeIn(Motion.snappy()),
            exit = scaleOut(Motion.snappy(), targetScale = 0.65f) + fadeOut(Motion.snappy()),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(if (dense) 4.dp else 6.dp),
        ) {
            FavoriteHeart(
                isFavorite = isFavorite,
                compact = dense,
                onToggle = onToggleFav,
            )
        }

        if (!dense) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(if (video.isVideo) 0.62f else 0.88f)
                    .padding(start = 9.dp, bottom = 8.dp),
            )
        }

        if (video.isVideo) {
            InfoBadge(
                durationMs = video.duration,
                compact = dense,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = if (dense) 4.dp else 7.dp, bottom = if (dense) 4.dp else 7.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassBorder(cardShape),
        )

        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(Motion.snappy()),
            exit = fadeOut(Motion.snappy()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.13f))
                    .border(2.dp, MaterialTheme.colorScheme.primary, cardShape),
            )
        }
        AnimatedVisibility(
            visible = selectionMode,
            enter = scaleIn(Motion.bouncy(), initialScale = 0.4f) + fadeIn(Motion.snappy()),
            exit = scaleOut(Motion.snappy(), targetScale = 0.4f) + fadeOut(Motion.snappy()),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(if (dense) 5.dp else 7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(if (dense) 21.dp else 23.dp)
                    .clip(PillShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.42f)
                    )
                    .border(
                        width = 1.25.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = 0.72f),
                        shape = PillShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visible = selected,
                    enter = scaleIn(Motion.bouncy(), initialScale = 0.3f) + fadeIn(Motion.snappy()),
                    exit = scaleOut(Motion.snappy()) + fadeOut(Motion.snappy()),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = Color(0xFF1A1030),
                        modifier = Modifier.size(if (dense) 13.dp else 15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteHeart(
    isFavorite: Boolean,
    compact: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val haptic = LocalHapticFeedback.current
    var lastTapAt by remember { mutableLongStateOf(0L) }
    val iconScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.10f else 1f,
        animationSpec = Motion.bouncy(),
        label = "heart-pop",
    )
    val buttonSize = if (compact) 28.dp else 31.dp
    val touchSize = if (compact) 44.dp else 48.dp

    // The larger unclipped host gives the shockwave and eight liquid droplets
    // enough room to bloom without shrinking the actual heart control.
    Box(
        modifier = modifier
            .size(touchSize)
            .favoriteBurst(isOn = isFavorite),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .pressBounce(interaction, pressedScale = 0.76f, spec = Motion.snappy())
                .size(buttonSize)
                .clip(PillShape)
                .background(
                    if (isFavorite) Color(0xFF7B2747).copy(alpha = 0.72f)
                    else Color.Black.copy(alpha = 0.40f)
                )
                .liquidGlassBorder(PillShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                ) {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastTapAt >= 170L) {
                        lastTapAt = now
                        // Contain haptic and persistence failures together; a
                        // heart interaction can never close the app.
                        runCatching {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggle()
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = isFavorite,
                transitionSpec = {
                    scaleIn(Motion.bouncy()) + fadeIn(Motion.snappy()) togetherWith
                        scaleOut(Motion.snappy()) + fadeOut(Motion.snappy())
                },
                label = "heart-icon",
            ) { favorite ->
                Icon(
                    imageVector = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (favorite) "Remove favorite" else "Add favorite",
                    tint = if (favorite) Color(0xFFFF739D) else Color.White,
                    modifier = Modifier
                        .size(if (compact) 15.dp else 17.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
            }
        }
    }
}

@Composable
private fun InfoBadge(
    durationMs: Long,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(Color.Black.copy(alpha = 0.56f))
            .padding(
                horizontal = if (compact) 4.dp else 6.dp,
                vertical = if (compact) 2.dp else 3.dp,
            ),
    ) {
        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.labelSmall.merge(TabularFigures),
            color = Color.White.copy(alpha = 0.94f),
            maxLines = 1,
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// ── Timeline ────────────────────────────────────────────────────────────────

@Composable
private fun TimelineHeader(
    label: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier
            .then(modifier)
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.merge(TabularFigures),
            color = Color.White.copy(alpha = 0.90f),
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.18f),
                            Color.Transparent,
                        )
                    )
                ),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium.merge(TabularFigures),
            color = Color.White.copy(alpha = 0.36f),
        )
    }
}

/** Right-edge date rail that stays quiet until touched, then blooms into glass. */
@Composable
private fun TimelineScrubber(
    jumps: List<TimelineJump>,
    currentLabel: String,
    onJump: (TimelineJump) -> Unit,
) {
    if (jumps.isEmpty()) return

    var dragging by remember { mutableStateOf(false) }
    var activeIndex by remember { mutableStateOf(0) }
    var railSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(currentLabel, jumps, dragging) {
        if (!dragging) {
            val index = jumps.indexOfFirst { it.label == currentLabel }
            if (index >= 0) activeIndex = index
        }
    }

    val railWidth by animateDpAsState(
        targetValue = if (dragging) 7.dp else 3.dp,
        animationSpec = Motion.expressive(),
        label = "timeline-rail-width",
    )
    val thumbSize by animateDpAsState(
        targetValue = if (dragging) 12.dp else 7.dp,
        animationSpec = Motion.bouncy(),
        label = "timeline-thumb-size",
    )

    BoxWithConstraints(
        modifier = Modifier
            .width(172.dp)
            .fillMaxHeight(),
    ) {
        val fraction = if (jumps.size <= 1) 0f
        else activeIndex.coerceIn(jumps.indices).toFloat() / jumps.lastIndex.toFloat()
        val thumbOffset = (maxHeight - thumbSize) * fraction

        AnimatedVisibility(
            visible = dragging,
            enter = fadeIn(Motion.standard()) + scaleIn(Motion.expressive(), initialScale = 0.82f),
            exit = fadeOut(Motion.snappy()) + scaleOut(Motion.snappy(), targetScale = 0.88f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = thumbOffset)
                .padding(end = 22.dp),
        ) {
            Box(
                modifier = Modifier
                    .clip(PillShape)
                    .liquidGlass(tint = Color(0xFF211A3C), alpha = 0.82f)
                    .opticalGlass(intensity = 0.86f, light = Offset(0.16f, 0.06f))
                    .liquidGlassBorder(PillShape)
                    .padding(horizontal = 11.dp, vertical = 7.dp),
            ) {
                Text(
                    text = jumps[activeIndex.coerceIn(jumps.indices)].label,
                    style = MaterialTheme.typography.labelMedium.merge(TabularFigures),
                    color = Color.White,
                    maxLines = 1,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(28.dp)
                .fillMaxHeight()
                .onSizeChanged { railSize = it }
                .pointerInput(jumps) {
                    fun selectAt(y: Float) {
                        if (railSize.height <= 0) return
                        val ratio = (y / railSize.height.toFloat()).coerceIn(0f, 1f)
                        val index = (ratio * jumps.lastIndex).toInt().coerceIn(jumps.indices)
                        if (index != activeIndex || !dragging) {
                            activeIndex = index
                            onJump(jumps[index])
                        }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        dragging = true
                        selectAt(down.position.y)
                        var pressed: Boolean
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            pressed = change?.pressed == true
                            if (change != null && pressed) {
                                selectAt(change.position.y)
                                change.consume()
                            }
                        } while (pressed)
                        dragging = false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(railWidth)
                    .fillMaxHeight()
                    .clip(PillShape)
                    .background(
                        Color.White.copy(alpha = if (dragging) 0.22f else 0.10f)
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = thumbOffset)
                    .size(thumbSize)
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = if (dragging) 1f else 0.72f))
                    .liquidGlassBorder(PillShape),
            )
        }
    }
}

/** Two-finger-only grid density gesture; one-finger scrolling remains untouched. */
private fun Modifier.pinchToResizeGrid(onStep: (Int) -> Unit): Modifier =
    pointerInput(onStep) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var accumulatedZoom = 1f
            var stepSent = false
            do {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } >= 2) {
                    val zoom = event.calculateZoom()
                    if (zoom.isFinite() && zoom > 0f) accumulatedZoom *= zoom
                    event.changes.forEach { it.consume() }
                    if (!stepSent && accumulatedZoom >= 1.16f) {
                        onStep(-1) // pinch out: larger cards, fewer columns
                        stepSent = true
                    } else if (!stepSent && accumulatedZoom <= 0.86f) {
                        onStep(1) // pinch in: smaller cards, more columns
                        stepSent = true
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }

// ── Albums ──────────────────────────────────────────────────────────────────

@Composable
private fun AlbumShelf(
    albums: List<com.darsma.glassgallery.ui.gallery.Album>,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPad: androidx.compose.ui.unit.Dp,
    onOpen: (String) -> Unit,
    onOpenTrash: () -> Unit,
) {
    LazyVerticalGrid(
        columns               = GridCells.Fixed(2),
        modifier              = Modifier.fillMaxSize(),
        contentPadding        = PaddingValues(
            start = 14.dp, end = 14.dp,
            top = topPadding, bottom = bottomPad,
        ),
        verticalArrangement   = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        itemsIndexed(albums, key = { _, a -> "album-${a.name}" }) { index, album ->
            AlbumCard(
                album    = album,
                index    = index,
                onClick  = { onOpen(album.name) },
                modifier = Modifier.animateItem(
                    fadeInSpec    = Motion.standard(),
                    placementSpec = Motion.expressive(),
                    fadeOutSpec   = Motion.snappy(),
                ),
            )
        }
        item(key = "album-trash") {
            TrashCard(onClick = onOpenTrash, modifier = Modifier.animateItem())
        }
    }
}

@Composable
private fun AlbumCard(
    album: Album,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Corner morph on press — same liquid language as the media cards.
    val corner by animateDpAsState(
        targetValue   = if (pressed) 34.dp else 22.dp,
        animationSpec = Motion.expressive(),
        label         = "album-corner",
    )
    val shape = RoundedCornerShape(corner)

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(10) * 34L)
        appeared = true
    }
    val appear by animateFloatAsState(
        targetValue   = if (appeared) 1f else 0f,
        animationSpec = Motion.expressive(),
        label         = "album-appear",
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                alpha        = appear.coerceIn(0f, 1f)
                translationY = (1f - appear) * 52f
                val sc = 0.92f + 0.08f * appear
                scaleX = sc; scaleY = sc
            }
            .pressBounce(interaction, pressedScale = 0.95f)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.05f))
            .liquidGlassBorder(shape)
            .clickable(
                interactionSource = interaction,
                indication        = null,
                onClick           = onClick,
            ),
    ) {
        AsyncImage(
            model              = ImageRequest.Builder(LocalContext.current)
                .data(album.cover.thumbnailUri)
                .crossfade(280)
                .build(),
            contentDescription = album.name,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f)
                .clip(RoundedCornerShape(topStart = corner, topEnd = corner)),
        )
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text       = album.name,
                style      = MaterialTheme.typography.titleMedium,
                color      = Color.White,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Text(
                text     = "${album.count} items",
                style    = MaterialTheme.typography.labelMedium.merge(TabularFigures),
                color    = Color.White.copy(alpha = 0.50f),
            )
        }
    }
}

@Composable
private fun TrashCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .pressBounce(interaction, pressedScale = 0.95f)
            .clip(shape)
            .background(Color(0xFFFF7A7A).copy(alpha = 0.07f))
            .liquidGlassBorder(shape)
            .clickable(
                interactionSource = interaction,
                indication        = null,
                onClick           = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.15f),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint               = Color(0xFFFF9B9B),
                modifier           = Modifier.size(44.dp),
            )
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text       = "Recently Deleted",
                style      = MaterialTheme.typography.titleMedium,
                color      = Color.White,
                maxLines   = 1,
            )
            Text(
                text     = "30-day recycle bin",
                style    = MaterialTheme.typography.labelMedium.merge(TabularFigures),
                color    = Color.White.copy(alpha = 0.50f),
            )
        }
    }
}

@Composable
private fun AlbumBackChip(
    name: String,
    count: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.then(modifier).padding(top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .pressBounce(interaction, pressedScale = 0.93f)
                .clip(PillShape)
                .liquidGlass(alpha = 0.55f)
                .liquidGlassBorder(PillShape)
                .clickable(
                    interactionSource = interaction,
                    indication        = null,
                    onClick           = onBack,
                )
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ChevronLeft, "All albums", tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                text       = name,
                style      = MaterialTheme.typography.titleMedium,
                color      = Color.White,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "$count items",
            style = MaterialTheme.typography.labelMedium.merge(TabularFigures),
            color = Color.White.copy(alpha = 0.45f),
        )
    }
}
