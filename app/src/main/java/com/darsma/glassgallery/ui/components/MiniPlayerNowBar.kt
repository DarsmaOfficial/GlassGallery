@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.darsma.glassgallery.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.darsma.glassgallery.data.Video

private val CapsuleShape = RoundedCornerShape(24.dp)
private val PillShape    = RoundedCornerShape(50)

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
            animationSpec  = Motion.expressive(),
        ) + fadeIn(Motion.standard()),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = Motion.expressive(),
        ) + fadeOut(),
        modifier = modifier.fillMaxWidth(),
    ) {
        video ?: return@AnimatedVisibility

        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val capsuleScale by animateFloatAsState(
            targetValue   = if (pressed) 0.97f else 1f,
            animationSpec = Motion.bouncy(),
            label         = "capsule-scale",
        )

        with(sharedTransitionScope) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .height(76.dp)
                    .sharedBounds(
                        sharedContentState      = rememberSharedContentState(
                            key = "video-surface-${video.id}"
                        ),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = { _, _ ->
                            Motion.spatial()
                        },
                    )
                    .scale(capsuleScale)
                    .clip(CapsuleShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF221A47),
                                Color(0xFF2A2156),
                                Color(0xFF201842),
                            )
                        )
                    )
                    .opticalGlass(intensity = 0.82f)
                    .specularFlash(trigger = video.id)
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
                            .width(104.dp)
                            .fillMaxHeight()
                            .clip(
                                RoundedCornerShape(
                                    topStart    = 24.dp,
                                    bottomStart = 24.dp,
                                    topEnd      = 0.dp,
                                    bottomEnd   = 0.dp,
                                )
                            ),
                    ) {
                        AsyncImage(
                            model              = ImageRequest.Builder(LocalContext.current)
                .data(video.thumbnailUri)
                .crossfade(280)
                .build(),
                            contentDescription = null,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize(),
                        )
                        // Gradient fade into the card body.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color(0xCC221A47),
                                        )
                                    )
                                )
                        )
                    }

                    Spacer(Modifier.width(13.dp))

                    // ── Title + sub-row ───────────────────────────────────
                    Column(
                        modifier            = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text       = video.title,
                            style      = MaterialTheme.typography.titleMedium,
                            color      = Color.White,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                            // Long titles glide horizontally instead of clipping.
                            modifier   = Modifier.basicMarquee(),
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text     = "Now Playing",
                            style    = MaterialTheme.typography.labelMedium,
                            color    = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        )
                        Spacer(Modifier.height(7.dp))
                        MiniProgressBar(progress = progress)
                    }

                    Spacer(Modifier.width(12.dp))

                    // ── Play / Pause — live morphing scallop ──────────────
                    MorphingPlayPauseButton(
                        isPlaying = isPlaying,
                        onClick   = onPlayPause,
                        size      = 48.dp,
                        iconSize  = 24.dp,
                        color     = MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
                    )
                }

                // Hairline glass edge over the whole capsule.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .liquidGlassBorder(CapsuleShape)
                )
            }
        }
    }
}

@Composable
private fun MiniProgressBar(progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue   = progress.coerceIn(0f, 1f),
        animationSpec = Motion.standard(),
        label         = "mini-progress",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(PillShape)
            .background(Color.White.copy(alpha = 0.15f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .height(3.dp)
                .clip(PillShape)
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
