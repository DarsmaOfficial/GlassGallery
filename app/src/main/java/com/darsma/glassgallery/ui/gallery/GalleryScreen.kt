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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.darsma.glassgallery.R
import com.darsma.glassgallery.data.Video
import com.darsma.glassgallery.ui.components.MiniPlayer
import com.darsma.glassgallery.ui.components.liquidGlass

private val ThumbShape = RoundedCornerShape(18.dp)

@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel,
    animatedVisibilityScope: AnimatedVisibilityScope,
    sharedTransitionScope: SharedTransitionScope,
    onVideoClick: (Video) -> Unit,
    onMiniPlayerClick: () -> Unit,
) {
    val context = LocalContext.current
    val uiState      by viewModel.uiState.collectAsState()
    val currentVideo by viewModel.currentVideo.collectAsState()

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

    Scaffold(
        modifier       = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar      = {
            MiniPlayer(
                video                   = currentVideo,
                isPlaying               = false,
                onPlayPause             = {},
                onClick                 = onMiniPlayerClick,
                sharedTransitionScope   = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
            )
        },
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .liquidGlass(alpha = 0.40f),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text       = stringResource(R.string.app_name),
                    style      = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    modifier   = Modifier.padding(horizontal = 24.dp),
                )
            }

            when (val state = uiState) {
                is GalleryUiState.Loading -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

                is GalleryUiState.PermissionRequired -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Text(
                        text     = stringResource(R.string.permission_rationale),
                        color    = MaterialTheme.colorScheme.onSurface,
                        style    = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(32.dp),
                    )
                }

                is GalleryUiState.Error -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Text(
                        text     = state.message,
                        color    = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(32.dp),
                    )
                }

                is GalleryUiState.Success -> with(sharedTransitionScope) {
                    LazyVerticalGrid(
                        columns               = GridCells.Adaptive(150.dp),
                        modifier              = Modifier.fillMaxSize(),
                        contentPadding        = PaddingValues(10.dp),
                        verticalArrangement   = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.videos, key = { it.id }) { video ->
                            VideoCard(
                                video                   = video,
                                sharedTransitionScope   = this@with,
                                animatedVisibilityScope = animatedVisibilityScope,
                                onClick                 = { onVideoClick(video) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec  = spring(stiffness = Spring.StiffnessMediumLow),
                                    fadeOutSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    placementSpec = spring(
                                        dampingRatio = 0.75f,
                                        stiffness    = Spring.StiffnessMediumLow,
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoCard(
    video: Video,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // Springy press feedback.
    val cardScale by animateFloatAsState(
        targetValue   = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "card-press",
    )

    with(sharedTransitionScope) {
        Box(
            modifier = modifier
                .scale(cardScale)
                .aspectRatio(16f / 9f)
                // The video frame itself is the shared element that morphs
                // into the full-screen player surface.
                .sharedBounds(
                    sharedContentState      = rememberSharedContentState("video-surface-${video.id}"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = { _, _ ->
                        spring(dampingRatio = 0.82f, stiffness = 340f)
                    },
                )
                .clip(ThumbShape)
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(52.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(
                    text     = video.title,
                    style    = MaterialTheme.typography.labelMedium,
                    color    = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
