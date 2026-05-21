@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.darsma.glassgallery.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.darsma.glassgallery.data.Video

private val CapsuleShape = RoundedCornerShape(22.dp)

@Composable
fun MiniPlayer(
    video: Video?,
    isPlaying: Boolean,
    progress: Float,               // 0f–1f playback progress for the mini bar
    onPlayPause: () -> Unit,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = video != null,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec  = spring(dampingRatio = 0.75f, stiffness = 360f),
        ) + fadeIn(spring(stiffness = Spring.StiffnessLow)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = spring(dampingRatio = 0.75f, stiffness = 360f),
        ) + fadeOut(),
        modifier = modifier.fillMaxWidth(),
    ) {
        video ?: return@AnimatedVisibility

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val capsuleScale by animateFloatAsState(
            targetValue   = if (pressed) 0.97f else 1f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
            label         = "capsule-scale",
        )

        with(sharedTransitionScope) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .height(72.dp)
                    .sharedBounds(
                        sharedContentState      = rememberSharedContentState(
                            key = "video-surface-${video.id}"
                        ),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = { _, _ ->
                            spring(dampingRatio = 0.82f, stiffness = 340f)
                        },
                    )
                    .scale(capsuleScale)
                    .clip(CapsuleShape)
                    // Rich gradient background
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF1E1540),
                                Color(0xFF251C4A),
                                Color(0xFF1E1540),
                            )
                        )
                    )
                    .clickable(
                        interactionSource = interaction,
                        indication        = null,
                        onClick           = onClick,
                    ),
            ) {
                Row(
                    modifier          = Modifier
                        .fillMaxSize()
                        .padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // ── Thumbnail ─────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(72.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart     = 22.dp,
                                    bottomStart  = 22.dp,
                                    topEnd       = 0.dp,
                                    bottomEnd    = 0.dp,
                                )
                            ),
                    ) {
                        AsyncImage(
                            model              = video.thumbnailUri,
                            contentDescription = null,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize(),
                        )
                        // gradient fade into card body
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color(0x881E1540),
                                        )
                                    )
                                )
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    // ── Title + sub-row ───────────────────────────────────
                    Column(
                        modifier         = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text       = video.title,
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color      = Color.White,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text  = "Now Playing",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.45f),
                        )
                        Spacer(Modifier.height(6.dp))
                        // Mini progress bar
                        MiniProgressBar(progress = progress)
                    }

                    Spacer(Modifier.width(10.dp))

                    // ── Play / Pause ──────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                                    )
                                )
                            )
                            .clickable(onClick = onPlayPause),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector        = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint               = Color.White,
                            modifier           = Modifier.size(26.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniProgressBar(progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue   = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label         = "mini-progress",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.15f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .height(3.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        )
                    )
                )
        )
    }
}
