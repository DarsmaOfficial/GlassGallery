@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.darsma.glassgallery.ui.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.darsma.glassgallery.R
import com.darsma.glassgallery.data.Video
import com.darsma.glassgallery.data.formatBytes
import com.darsma.glassgallery.ui.components.BouncyIconButton
import com.darsma.glassgallery.ui.components.Motion
import com.darsma.glassgallery.ui.components.MiniPlayer
import com.darsma.glassgallery.ui.components.liquidGlassBorder
import com.darsma.glassgallery.ui.components.pressBounce
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
    val hasMiniPlayer = currentVideo != null

    var sortSheetVisible by remember { mutableStateOf(false) }
    var searchExpanded   by remember { mutableStateOf(false) }

    val permission = if (Build.VERSION.SDK_INT >= 33)
        Manifest.permission.READ_MEDIA_VIDEO
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    var hasPermission by remember {
        mutableStateOf(context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED)
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) viewModel.onPermissionGranted() else viewModel.onPermissionDenied()
    }

    LaunchedEffect(Unit) {
        if (hasPermission) viewModel.onPermissionGranted() else permLauncher.launch(permission)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1C1538),
                            Color(0xFF120F22),
                            Color.Transparent,
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            val successState = uiState as? GalleryUiState.Success
            GalleryHeader(
                videoCount     = successState?.videos?.size ?: 0,
                totalSizeBytes = successState?.videos?.sumOf { it.sizeBytes } ?: 0L,
                favoritesOnly  = favoritesOnly,
                searchActive   = searchExpanded,
                onToggleFavs   = { viewModel.toggleFavoritesFilter() },
                onOpenSort     = { sortSheetVisible = true },
                onToggleSearch = {
                    searchExpanded = !searchExpanded
                    if (!searchExpanded) viewModel.setSearchQuery("")
                },
            )

            GallerySearchBar(
                expanded     = searchExpanded,
                query        = searchQuery,
                resultCount  = successState?.videos?.size ?: 0,
                onQueryChange = { viewModel.setSearchQuery(it) },
                onClose      = {
                    searchExpanded = false
                    viewModel.setSearchQuery("")
                },
            )

            when (val state = uiState) {
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
                    if (state.videos.isEmpty()) {
                        CenterBox {
                            Text(
                                text     = if (favoritesOnly)
                                    "No favorites yet.\nTap the heart on a video to add one."
                                else
                                    "No videos found.",
                                color     = Color.White.copy(alpha = 0.55f),
                                fontSize  = 15.sp,
                                modifier  = Modifier.padding(32.dp),
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            columns               = GridCells.Fixed(2),
                            modifier              = Modifier.fillMaxSize(),
                            contentPadding         = PaddingValues(
                                start  = 14.dp,
                                end    = 14.dp,
                                top    = 4.dp,
                                bottom = if (hasMiniPlayer) MINI_PLAYER_HEIGHT + 18.dp else 18.dp,
                            ),
                            verticalArrangement    = Arrangement.spacedBy(14.dp),
                            horizontalArrangement  = Arrangement.spacedBy(14.dp),
                        ) {
                            itemsIndexed(
                                items = state.videos,
                                key   = { _, video -> video.id },
                            ) { index, video ->
                                VideoCard(
                                    video       = video,
                                    index       = index,
                                    isFavorite  = video.id in favorites,
                                    onClick     = { onVideoClick(video) },
                                    onToggleFav = { viewModel.toggleFavorite(video.id) },
                                    modifier    = Modifier.animateItem(
                                        placementSpec = Motion.standard(),
                                    ),
                                )
                            }
                        }
                    }
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
                .navigationBarsPadding(),
        )

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
    videoCount: Int,
    totalSizeBytes: Long,
    favoritesOnly: Boolean,
    searchActive: Boolean,
    onToggleFavs: () -> Unit,
    onOpenSort: () -> Unit,
    onToggleSearch: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
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
            )
            Spacer(Modifier.height(4.dp))
            // Subtitle cross-fades whenever the count/size changes.
            AnimatedContent(
                targetState    = videoCount to totalSizeBytes,
                transitionSpec = {
                    (fadeIn(Motion.standard()) togetherWith fadeOut(Motion.snappy()))
                },
                label = "gallery-subtitle",
            ) { (count, size) ->
                Text(
                    text     = when (count) {
                        0    -> if (favoritesOnly) "No favorites" else "Your library"
                        else -> "$count ${if (count == 1) "video" else "videos"}  ·  ${formatBytes(size)}"
                    },
                    fontSize = 13.sp,
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
                )
            }
        }

        // Search toggle.
        val searchTint by animateFloatAsState(
            targetValue   = if (searchActive) 1f else 0f,
            animationSpec = Motion.standard(),
            label         = "search-tint",
        )
        BouncyIconButton(
            onClick    = onToggleSearch,
            size       = 44.dp,
            background = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f + 0.22f * searchTint),
        ) {
            Icon(
                imageVector        = Icons.Rounded.Search,
                contentDescription = "Search videos",
                tint               = if (searchActive) MaterialTheme.colorScheme.primary else Color.White,
                modifier           = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(8.dp))

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
                contentDescription = "Sort videos",
                tint               = Color.White,
                modifier           = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun VideoCard(
    video: Video,
    index: Int,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFav: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardInteraction = remember { MutableInteractionSource() }

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(14) * 28L)
        appeared = true
    }
    val appear by animateFloatAsState(
        targetValue   = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 440),
        label         = "card-appear",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                alpha        = appear
                translationY = (1f - appear) * 46f
            }
            .pressBounce(cardInteraction, pressedScale = 0.955f, spec = Motion.standard())
            .aspectRatio(16f / 9f)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                interactionSource = cardInteraction,
                indication        = null,
                onClick           = onClick,
            ),
    ) {
        AsyncImage(
            model              = ImageRequest.Builder(LocalContext.current)
                .data(video.thumbnailUri)
                .crossfade(280)
                .build(),
            contentDescription = video.title,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize(),
        )

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

        // Centered glass play affordance.
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

        // Favorite heart — top-right.
        FavoriteHeart(
            isFavorite = isFavorite,
            onToggle   = onToggleFav,
            modifier   = Modifier
                .align(Alignment.TopEnd)
                .padding(7.dp),
        )

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
            modifier   = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 9.dp, bottom = 9.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassBorder(CardShape)
        )
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
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color.Black.copy(alpha = 0.58f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text       = "${formatDuration(durationMs)}  ·  ${formatBytes(sizeBytes)}",
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
