@file:OptIn(ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class)

package com.darsma.glassgallery.ui.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.BasicTextField
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
import com.darsma.glassgallery.ui.components.LiquidTabBar
import com.darsma.glassgallery.ui.components.liquidGlass
import com.darsma.glassgallery.ui.components.Motion
import com.darsma.glassgallery.ui.components.MiniPlayer
import com.darsma.glassgallery.ui.components.glassSheen
import com.darsma.glassgallery.ui.components.liquidGlassBorder
import com.darsma.glassgallery.ui.components.liquidHighlight
import com.darsma.glassgallery.ui.components.pressBounce
import com.darsma.glassgallery.ui.components.shimmer
import com.darsma.glassgallery.ui.components.favoriteBurst
import kotlinx.coroutines.delay

private val CardShape = RoundedCornerShape(20.dp)
private val PillShape = RoundedCornerShape(50)
private val MINI_PLAYER_HEIGHT = 92.dp

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
    val searchQuery       by viewModel.searchQuery.collectAsState()
    val mediaFilter       by viewModel.mediaFilter.collectAsState()
    val selectedIds       by viewModel.selectedIds.collectAsState()
    val albums            by viewModel.albums.collectAsState()
    val openAlbum         by viewModel.openAlbum.collectAsState()
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

    // Hoisted grid state — drives the scroll-aware glass header.
    val gridState = rememberLazyGridState()
    val headerScrolled by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 16
        }
    }
    val headerGlass by animateFloatAsState(
        targetValue   = if (headerScrolled) 1f else 0f,
        animationSpec = Motion.standard(),
        label         = "header-glass",
    )
    // Photos tab switches the grid to a tight 3-column square mosaic;
    // animateItem morphs every tile into its new position.
    val compactGrid = mediaFilter == MediaFilter.PHOTOS

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
    val topInset    = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Drifting ambient colour the glass can "refract".
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
                        // Albums tab root: folder shelf + Recently Deleted card.
                        AlbumShelf(
                            albums      = albums,
                            topInset    = topInset,
                            bottomPad   = 86.dp + if (hasMiniPlayer) MINI_PLAYER_HEIGHT else 0.dp,
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
                                fontSize  = 15.sp,
                                modifier  = Modifier.padding(32.dp),
                            )
                        }
                    } else {
                        val gap = if (compactGrid) 4.dp else 14.dp
                        LazyVerticalGrid(
                            columns               = GridCells.Fixed(if (compactGrid) 3 else 2),
                            state                 = gridState,
                            modifier              = Modifier.fillMaxSize(),
                            contentPadding         = PaddingValues(
                                start  = gap,
                                end    = gap,
                                top    = topInset + 112.dp,
                                bottom = 86.dp + if (hasMiniPlayer) MINI_PLAYER_HEIGHT else 0.dp,
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
                                        compact       = compactGrid,
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

        // ── Floating glass header: transparent at rest, frosts on scroll ──
        val successState = uiState as? GalleryUiState.Success
        val mediaList    = successState?.videos ?: emptyList()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0B0918).copy(alpha = 0.74f * headerGlass))
                .statusBarsPadding(),
        ) {
            GalleryHeader(
                collapse       = headerGlass,
                videoCount     = mediaList.count { it.isVideo },
                photoCount     = mediaList.count { !it.isVideo },
                totalSizeBytes = mediaList.sumOf { it.sizeBytes },
                favoritesOnly  = favoritesOnly,
                searchActive   = searchExpanded,
                searchQuery    = searchQuery,
                onQueryChange  = { viewModel.setSearchQuery(it) },
                onToggleFavs   = { viewModel.toggleFavoritesFilter() },
                onOpenSort     = { sortSheetVisible = true },
                onToggleSearch = {
                    searchExpanded = !searchExpanded
                    if (!searchExpanded) viewModel.setSearchQuery("")
                },
            )
        }

        // Frosted date chip that "sticks" under the header while scrolling.
        AnimatedVisibility(
            visible  = headerScrolled && stickyLabel != null &&
                       !(mediaFilter == MediaFilter.ALBUMS && openAlbum == null),
            enter    = slideInVertically(initialOffsetY = { -it }, animationSpec = Motion.expressive()) +
                       fadeIn(Motion.standard()),
            exit     = slideOutVertically(targetOffsetY = { -it }, animationSpec = Motion.snappy()) +
                       fadeOut(Motion.snappy()),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 100.dp),
        ) {
            AnimatedContent(
                targetState    = stickyLabel ?: "",
                transitionSpec = {
                    (slideInVertically(initialOffsetY = { it / 2 }, animationSpec = Motion.expressive()) +
                        fadeIn(Motion.standard())) togetherWith
                        (slideOutVertically(targetOffsetY = { -it / 2 }, animationSpec = Motion.snappy()) +
                            fadeOut(Motion.snappy()))
                },
                label = "sticky-date",
            ) { label ->
                Box(
                    modifier = Modifier
                        .clip(PillShape)
                        .liquidGlass(alpha = 0.66f)
                        .glassSheen()
                        .liquidGlassBorder(PillShape)
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                ) {
                    Text(
                        text       = label,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color      = Color.White,
                    )
                }
            }
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
                .padding(bottom = 62.dp),
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
                modifier      = Modifier.fillMaxWidth(0.86f),
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
                    .height(54.dp)
                    .clip(PillShape)
                    .liquidGlass(alpha = 0.62f)
                    .glassSheen()
                    .liquidHighlight()
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
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color      = MaterialTheme.colorScheme.primary,
                        modifier   = Modifier.padding(start = 10.dp),
                    )
                }
                Text(
                    text     = " selected",
                    fontSize = 13.sp,
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
    searchActive: Boolean,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    onToggleFavs: () -> Unit,
    onOpenSort: () -> Unit,
    onToggleSearch: () -> Unit,
) {
    // One spring drives the entire container-transform: 0 = a 44 dp circular
    // icon button, 1 = a full-width pill search field. Everything else in the
    // header recedes in lock-step with the same value.
    val searchProgress by animateFloatAsState(
        targetValue   = if (searchActive) 1f else 0f,
        animationSpec = Motion.expressive(),
        label         = "search-morph",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
    ) {
        val fullWidth = maxWidth

        // ── Layer 1: title + chrome — recedes as the search bar grows ────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha        = (1f - searchProgress).coerceIn(0f, 1f)
                    translationX = -30f * searchProgress
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = stringResource(R.string.app_name),
                    fontSize   = 32.sp,
                    fontWeight = FontWeight.Bold,
                    style      = MaterialTheme.typography.headlineLarge.copy(
                        brush = Brush.linearGradient(
                            colors = listOf(Color(0xFFF3EEFF), Color(0xFFBBA6F2)),
                        ),
                    ),
                    // Large-title collapse: shrinks toward its leading edge as
                    // the grid scrolls up and the header frosts.
                    modifier = Modifier.graphicsLayer {
                        val sc = 1f - 0.22f * collapse
                        scaleX = sc
                        scaleY = sc
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    },
                )
                Spacer(Modifier.height(4.dp))
                AnimatedContent(
                    targetState    = Triple(videoCount, photoCount, totalSizeBytes),
                    transitionSpec = {
                        (fadeIn(Motion.standard()) togetherWith fadeOut(Motion.snappy()))
                    },
                    label = "gallery-subtitle",
                ) { (videos, photos, size) ->
                    Text(
                        text     = when {
                            videos == 0 && photos == 0 ->
                                if (favoritesOnly) "No favorites" else "Your library"
                            else ->
                                "$videos videos  ·  $photos photos  ·  ${formatBytes(size)}"
                        },
                        fontSize = 13.sp,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                    )
                }
            }

            // Favorites filter toggle.
            val favTint by animateFloatAsState(
                targetValue   = if (favoritesOnly) 1f else 0f,
                animationSpec = Motion.standard(),
                label         = "fav-filter-tint",
            )
            BouncyIconButton(
                onClick    = onToggleFavs,
                size       = 44.dp,
                background = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f + 0.22f * favTint),
            ) {
                AnimatedContent(
                    targetState    = favoritesOnly,
                    transitionSpec = {
                        scaleIn(Motion.bouncy()) + fadeIn(Motion.snappy()) togetherWith
                            scaleOut(Motion.snappy()) + fadeOut(Motion.snappy())
                    },
                    label = "fav-filter-icon",
                ) { on ->
                    Icon(
                        imageVector        = if (on) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Show favorites only",
                        tint               = if (on) Color(0xFFFF5C8A) else Color.White,
                        modifier           = Modifier.size(21.dp),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            // Sort button.
            BouncyIconButton(
                onClick    = onOpenSort,
                size       = 44.dp,
                background = Color.White.copy(alpha = 0.10f),
            ) {
                Icon(
                    imageVector        = Icons.Rounded.SwapVert,
                    contentDescription = "Sort media",
                    tint               = Color.White,
                    modifier           = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            // Empty seat exactly where the morphing search circle sits.
            Spacer(Modifier.size(44.dp))
        }

        // ── Layer 2: the search morph itself ─────────────────────────────
        // A circle anchored at the row's end that blooms across the full
        // header width with an expressive overshoot — a true container
        // transform, not a crossfade.
        val barWidth  = lerp(44.dp, fullWidth, searchProgress.coerceIn(0f, 1f))
        val barHeight = lerp(44.dp, 52.dp, searchProgress.coerceIn(0f, 1f))

        val focusRequester = remember { FocusRequester() }
        val keyboard       = LocalSoftwareKeyboardController.current
        LaunchedEffect(searchActive) {
            if (searchActive) {
                delay(170L)               // let the bloom mostly land first
                focusRequester.requestFocus()
            } else {
                keyboard?.hide()
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(barWidth)
                .height(barHeight)
                .clip(PillShape)
                .background(
                    MaterialTheme.colorScheme.primary.copy(
                        alpha = 0.10f + 0.12f * searchProgress
                    )
                )
                .glassSheen()
                .liquidGlassBorder(PillShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    enabled           = !searchActive,
                    onClick           = onToggleSearch,
                ),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier          = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The lens never moves: centered in the collapsed circle, it
                // becomes the leading icon of the expanded bar.
                Icon(
                    imageVector        = Icons.Rounded.Search,
                    contentDescription = "Search media",
                    tint               = Color.White,
                    modifier           = Modifier
                        .padding(start = 11.dp)
                        .size(21.dp),
                )
                if (searchProgress > 0.25f) {
                    val contentAlpha = ((searchProgress - 0.25f) / 0.75f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp, end = 4.dp)
                            .graphicsLayer { alpha = contentAlpha },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text     = "Search videos & photos…",
                                fontSize = 14.sp,
                                color    = Color.White.copy(alpha = 0.40f),
                                maxLines = 1,
                            )
                        }
                        BasicTextField(
                            value         = searchQuery,
                            onValueChange = onQueryChange,
                            singleLine    = true,
                            textStyle     = TextStyle(color = Color.White, fontSize = 14.sp),
                            cursorBrush   = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier      = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                        )
                    }
                    Box(modifier = Modifier.graphicsLayer { alpha = contentAlpha }) {
                        BouncyIconButton(
                            onClick    = onToggleSearch,
                            size       = 36.dp,
                            background = Color.White.copy(alpha = 0.08f),
                        ) {
                            Icon(
                                imageVector        = Icons.Rounded.Close,
                                contentDescription = "Close search",
                                tint               = Color.White,
                                modifier           = Modifier.size(17.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                }
            }
        }
    }
}

/**
 * All / Videos / Photos segmented control. The selected indicator is a glass
 * pill that glides between segments with an expressive spring — the morphing
 * heart of media-type switching.
 */
@Composable
private fun MediaFilterTabs(
    current: MediaFilter,
    onSelect: (MediaFilter) -> Unit,
) {
    val options = listOf(
        MediaFilter.ALL    to "All",
        MediaFilter.VIDEOS to "Videos",
        MediaFilter.PHOTOS to "Photos",
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
            .height(42.dp)
            .clip(PillShape)
            .background(Color.White.copy(alpha = 0.06f))
            .liquidGlassBorder(PillShape),
    ) {
        val segWidth      = maxWidth / options.size
        val selectedIndex = options.indexOfFirst { it.first == current }.coerceAtLeast(0)
        val indicatorX by animateDpAsState(
            targetValue   = segWidth * selectedIndex,
            animationSpec = Motion.expressive(),
            label         = "tab-indicator",
        )
        Box(
            modifier = Modifier
                .offset(x = indicatorX)
                .width(segWidth)
                .fillMaxHeight()
                .padding(4.dp)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
                .liquidGlassBorder(PillShape),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEach { (filter, label) ->
                val selected = filter == current
                val textAlpha by animateFloatAsState(
                    targetValue   = if (selected) 1f else 0.55f,
                    animationSpec = Motion.standard(),
                    label         = "tab-text-alpha",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null,
                        ) { onSelect(filter) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = label,
                        fontSize   = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color      = Color.White.copy(alpha = textAlpha),
                    )
                }
            }
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
    compact: Boolean = false,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onLongPress: () -> Unit = {},
) {
    val cardInteraction = remember { MutableInteractionSource() }
    val cardPressed by cardInteraction.collectIsPressedAsState()
    // The card's silhouette itself morphs on touch: corners swell outward
    // with the press and spring back on release.
    val cardCorner by animateDpAsState(
        targetValue   = when {
            cardPressed -> if (compact) 24.dp else 32.dp
            compact     -> 12.dp
            else        -> 20.dp
        },
        animationSpec = Motion.expressive(),
        label         = "card-corner",
    )
    val cardShape = RoundedCornerShape(cardCorner)
    val selectScale by animateFloatAsState(
        targetValue   = if (selected) 0.90f else 1f,
        animationSpec = Motion.expressive(),
        label         = "card-select-scale",
    )

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(14) * 28L)
        appeared = true
    }
    val appear by animateFloatAsState(
        targetValue   = if (appeared) 1f else 0f,
        animationSpec = Motion.expressive(),
        label         = "card-appear",
    )

    // Photos get a true grid→fullscreen container morph. The key lives only
    // here and in PhotoViewerScreen — the video morph keys stay untouched on
    // the MiniPlayer/player pair.
    val photoMorph = if (!video.isVideo) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState      = rememberSharedContentState("photo-${video.id}"),
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform         = { _, _ -> Motion.expressive() },
            )
        }
    } else Modifier

    Box(
        modifier = modifier
            .graphicsLayer {
                alpha        = appear.coerceIn(0f, 1f)
                translationY = (1f - appear) * 46f
                // Gentle scale overshoot so each card "lands" with life;
                // selected tiles additionally sink inward Apple-style.
                val sc = (0.94f + 0.06f * appear) * selectScale
                scaleX = sc
                scaleY = sc
            }
            .pressBounce(cardInteraction, pressedScale = 0.955f, spec = Motion.standard())
            .aspectRatio(if (compact) 1f else 16f / 9f)
            .then(photoMorph)
            .clip(cardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                interactionSource = cardInteraction,
                indication        = null,
                onClick           = onClick,
                onLongClick       = onLongPress,
            ),
    ) {
        var thumbResolved by remember { mutableStateOf(false) }
        AsyncImage(
            model              = ImageRequest.Builder(LocalContext.current)
                .data(video.thumbnailUri)
                .crossfade(280)
                .build(),
            contentDescription = video.title,
            contentScale       = ContentScale.Crop,
            onState            = { st ->
                thumbResolved = st is AsyncImagePainter.State.Success ||
                    st is AsyncImagePainter.State.Error
            },
            modifier           = Modifier.fillMaxSize(),
        )
        // Shimmer placeholder — sweeps until the thumbnail resolves, then
        // fades away so loading never looks static.
        val shimmerAlpha by animateFloatAsState(
            targetValue   = if (thumbResolved) 0f else 1f,
            animationSpec = tween(durationMillis = 320),
            label         = "shimmer-fade",
        )
        if (shimmerAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = shimmerAlpha }
                    .shimmer()
            )
        }

        if (!compact) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.00f to Color.Transparent,
                            0.48f to Color.Transparent,
                            1.00f to Color.Black.copy(alpha = 0.78f),
                        )
                    )
            )
        }

        // Centered glass play affordance — videos only.
        if (video.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .clip(PillShape)
                    .background(Color.Black.copy(alpha = 0.30f))
                    .liquidGlassBorder(PillShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint               = Color.White,
                    modifier           = Modifier
                        .size(24.dp)
                        .padding(start = 2.dp),
                )
            }
        }

        // Favorite heart — top-right.
        FavoriteHeart(
            isFavorite = isFavorite,
            onToggle   = onToggleFav,
            modifier   = Modifier
                .align(Alignment.TopEnd)
                .padding(if (compact) 5.dp else 7.dp),
        )

        if (!compact) {
            // Title.
            Text(
                text       = video.title,
                style      = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color      = Color.White,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.62f)
                    .padding(start = 11.dp, bottom = 10.dp),
            )

            // Duration + size badge — bottom-right.
            InfoBadge(
                durationMs = video.duration,
                sizeBytes  = video.sizeBytes,
                isVideo    = video.isVideo,
                modifier   = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 9.dp, bottom = 9.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassBorder(cardShape)
        )

        // ── Selection chrome ───────────────────────────────────────────────
        AnimatedVisibility(
            visible = selected,
            enter   = fadeIn(Motion.snappy()),
            exit    = fadeOut(Motion.snappy()),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .border(2.dp, MaterialTheme.colorScheme.primary, cardShape)
            )
        }
        AnimatedVisibility(
            visible  = selectionMode,
            enter    = scaleIn(Motion.bouncy(), initialScale = 0.4f) + fadeIn(Motion.snappy()),
            exit     = scaleOut(Motion.snappy(), targetScale = 0.4f) + fadeOut(Motion.snappy()),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(if (compact) 5.dp else 7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(23.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.35f)
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else Color.White.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(50),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visible = selected,
                    enter   = scaleIn(Motion.bouncy(), initialScale = 0.3f) + fadeIn(Motion.snappy()),
                    exit    = scaleOut(Motion.snappy()) + fadeOut(Motion.snappy()),
                ) {
                    Icon(
                        imageVector        = Icons.Rounded.Check,
                        contentDescription = null,
                        tint               = Color(0xFF1A1030),
                        modifier           = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteHeart(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    // The heart pops with an overshoot every time it flips on.
    val pop by animateFloatAsState(
        targetValue   = if (isFavorite) 1f else 0f,
        animationSpec = Motion.bouncy(),
        label         = "heart-pop",
    )
    Box(
        modifier = modifier
            .pressBounce(interaction, pressedScale = 0.78f, spec = Motion.snappy())
            .size(32.dp)
            .favoriteBurst(isFavorite)
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.34f))
            .clickable(
                interactionSource = interaction,
                indication        = null,
                onClick           = onToggle,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState    = isFavorite,
            transitionSpec = {
                scaleIn(Motion.bouncy()) + fadeIn(Motion.snappy()) togetherWith
                    scaleOut(Motion.snappy()) + fadeOut(Motion.snappy())
            },
            label = "heart-icon",
        ) { fav ->
            Icon(
                imageVector        = if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (fav) "Remove favorite" else "Add favorite",
                tint               = if (fav) Color(0xFFFF5C8A) else Color.White,
                modifier           = Modifier
                    .size(17.dp)
                    .graphicsLayer {
                        // Subtle extra swell at the peak of the pop.
                        val s = 1f + 0.18f * pop
                        scaleX = s
                        scaleY = s
                    },
            )
        }
    }
}

@Composable
private fun InfoBadge(
    durationMs: Long,
    sizeBytes: Long,
    isVideo: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text       = if (isVideo)
                "${formatDuration(durationMs)}  ·  ${formatBytes(sizeBytes)}"
            else
                formatBytes(sizeBytes),
            fontSize   = 10.sp,
            fontWeight = FontWeight.Medium,
            color      = Color.White,
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
        modifier          = Modifier.then(modifier).padding(start = 4.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clip(PillShape)
                .background(Color.White.copy(alpha = 0.07f))
                .glassSheen()
                .liquidGlassBorder(PillShape)
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(
                text       = label,
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text     = "$count",
            fontSize = 12.sp,
            color    = Color.White.copy(alpha = 0.40f),
        )
    }
}

// ── Albums ──────────────────────────────────────────────────────────────────

@Composable
private fun AlbumShelf(
    albums: List<com.darsma.glassgallery.ui.gallery.Album>,
    topInset: androidx.compose.ui.unit.Dp,
    bottomPad: androidx.compose.ui.unit.Dp,
    onOpen: (String) -> Unit,
    onOpenTrash: () -> Unit,
) {
    LazyVerticalGrid(
        columns               = GridCells.Fixed(2),
        modifier              = Modifier.fillMaxSize(),
        contentPadding        = PaddingValues(
            start = 14.dp, end = 14.dp,
            top = topInset + 112.dp, bottom = bottomPad,
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
            .glassSheen()
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
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color      = Color.White,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            Text(
                text     = "${album.count} items",
                fontSize = 11.sp,
                color    = Color.White.copy(alpha = 0.50f),
            )
        }
    }
}

@Composable
private fun TrashCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .pressBounce(interaction, pressedScale = 0.95f)
            .clip(CardShape)
            .background(Color(0xFFFF7A7A).copy(alpha = 0.07f))
            .glassSheen()
            .liquidGlassBorder(CardShape)
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
                fontSize   = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color      = Color.White,
                maxLines   = 1,
            )
            Text(
                text     = "30-day recycle bin",
                fontSize = 11.sp,
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
                .glassSheen()
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
                fontSize   = 15.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text("$count items", fontSize = 12.sp, color = Color.White.copy(alpha = 0.45f))
    }
}
