@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.darsma.glassgallery.ui.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.draw.scale
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
import com.darsma.glassgallery.R
import com.darsma.glassgallery.data.Video
import com.darsma.glassgallery.ui.components.MiniPlayer
import com.darsma.glassgallery.ui.components.liquidGlassBorder
import kotlinx.coroutines.delay

private val CardShape = RoundedCornerShape(20.dp)
private val PillShape = RoundedCornerShape(50)

// Height the MiniPlayer occupies including its vertical padding.
private val MINI_PLAYER_HEIGHT = 92.dp

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    animatedVisibilityScope: AnimatedVisibilityScope,
    sharedTransitionScope: SharedTransitionScope,
    onVideoClick: (Video) -> Unit,
    onMiniPlayerClick: () -> Unit,
) {
    val context          = LocalContext.current
    val uiState          by viewModel.uiState.collectAsState()
    val currentVideo     by viewModel.currentVideo.collectAsState()
    val currentProgress  by viewModel.currentProgress.collectAsState()
    val currentIsPlaying by viewModel.currentIsPlaying.collectAsState()
    val hasMiniPlayer = currentVideo != null

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
        // Soft ambient glow behind everything — gives the dark UI depth.
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
            GalleryHeader(
                videoCount = (uiState as? GalleryUiState.Success)?.videos?.size ?: 0,
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
                    LazyVerticalGrid(
                        columns               = GridCells.Fixed(2),
                        modifier              = Modifier.fillMaxSize(),
                        contentPadding        = PaddingValues(
                            start  = 14.dp,
                            end    = 14.dp,
                            top    = 4.dp,
                            bottom = if (hasMiniPlayer) MINI_PLAYER_HEIGHT + 18.dp else 18.dp,
                        ),
                        verticalArrangement   = Arrangement.spacedBy(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        itemsIndexed(
                            items = state.videos,
                            key   = { _, video -> video.id },
                        ) { index, video ->
                            VideoCard(
                                video    = video,
                                index    = index,
                                onClick  = { onVideoClick(video) },
                                modifier = Modifier.animateItem(
                                    placementSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
                                ),
                            )
                        }
                    }
                }
            }
        }

        // MiniPlayer — anchored at the BOTTOM, full width. This is the ONLY
        // composable on the gallery side that carries the shared-element key,
        // so the player ALWAYS morphs straight down into this bar.
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
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun GalleryHeader(videoCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 12.dp),
    ) {
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
        Text(
            text     = when (videoCount) {
                0    -> "Your library"
                1    -> "1 video"
                else -> "$videoCount videos"
            },
            fontSize = 13.sp,
            color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.50f),
        )
    }
}

/**
 * A grid thumbnail. Intentionally NOT a shared element — the morph target is
 * always the bottom MiniPlayer, so the card stays a plain (animated) tile.
 */
@Composable
private fun VideoCard(
    video: Video,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue   = if (pressed) 0.955f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label         = "card-press",
    )

    // Staggered entrance: each card eases up + fades in, slightly after the last.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index.coerceAtMost(14) * 28L)
        appeared = true
    }
    val appear by animateFloatAsState(
        targetValue   = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 420),
        label         = "card-appear",
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                alpha        = appear
                translationY = (1f - appear) * 46f
            }
            .scale(pressScale)
            .aspectRatio(16f / 9f)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(
                interactionSource = interaction,
                indication        = null,
                onClick           = onClick,
            ),
    ) {
        AsyncImage(
            model              = video.thumbnailUri,
            contentDescription = video.title,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize(),
        )

        // Legibility scrim — transparent up top, darkening toward the title.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.52f to Color.Transparent,
                        1.00f to Color.Black.copy(alpha = 0.74f),
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

        // Title — bottom-left, leaves room for the duration badge.
        Text(
            text       = video.title,
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color      = Color.White,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
            modifier   = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.70f)
                .padding(start = 11.dp, bottom = 10.dp),
        )

        // Duration badge — bottom-right frosted pill.
        DurationBadge(
            durationMs = video.duration,
            modifier   = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 9.dp, bottom = 9.dp),
        )

        // Hairline glass edge over the whole card.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassBorder(CardShape)
        )
    }
}

@Composable
private fun DurationBadge(durationMs: Long, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text       = formatDuration(durationMs),
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
