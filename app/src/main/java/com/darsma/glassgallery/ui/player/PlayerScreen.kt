@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.darsma.glassgallery.ui.player

import android.content.ContentUris
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.darsma.glassgallery.data.MediaStoreVideoSource
import com.darsma.glassgallery.data.Video
import com.darsma.glassgallery.ui.components.SmoothSeekBar
import com.darsma.glassgallery.ui.components.liquidGlass
import kotlinx.coroutines.delay

private val ControlsShape = RoundedCornerShape(26.dp)

@Composable
fun PlayerScreen(
    videoId: Long,
    animatedVisibilityScope: AnimatedVisibilityScope,
    sharedTransitionScope: SharedTransitionScope,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    val video: Video? by produceState<Video?>(null, videoId) {
        value = MediaStoreVideoSource(context).loadVideoById(videoId)
    }

    val videoUri = remember(videoId) {
        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId)
    }

    val player = remember {
        ExoPlayer.Builder(context).build().also { p ->
            p.setMediaItem(MediaItem.fromUri(videoUri))
            p.prepare()
            p.playWhenReady = true
        }
    }

    DisposableEffect(player) { onDispose { player.release() } }

    var isPlaying       by remember { mutableStateOf(player.isPlaying) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration        by remember { mutableLongStateOf(0L) }
    var isSeeking       by remember { mutableStateOf(false) }
    var seekPreview     by remember { mutableLongStateOf(0L) }

    // Listener keeps play/pause state instant; polling only advances the clock.
    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        player.addListener(listener)
        while (true) {
            if (!isSeeking) currentPosition = player.currentPosition
            duration = if (player.duration != C.TIME_UNSET) player.duration else 0L
            delay(60L)   // ~16 fps clock; SmoothSeekBar springs between samples
        }
    }

    // Controls slide in shortly after the morph settles.
    var controlsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(180L)
        controlsVisible = true
    }

    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            // Video surface — shares the SAME key as the gallery thumbnail,
            // so the thumbnail morphs straight into the player video.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .sharedBounds(
                        sharedContentState      = rememberSharedContentState("video-surface-$videoId"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform = { _, _ ->
                            spring(dampingRatio = 0.82f, stiffness = 340f)
                        },
                    ),
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Top bar slides down + fades in.
            AnimatedVisibility(
                visible = controlsVisible,
                enter   = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec  = spring(dampingRatio = 0.8f, stiffness = 300f),
                ) + fadeIn(spring(stiffness = Spring.StiffnessLow)),
                exit    = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .systemBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .clip(ControlsShape)
                        .liquidGlass(alpha = 0.42f)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassIconButton(
                        onClick = onBack,
                        size    = 46.dp,
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint               = Color.White,
                        )
                    }
                    Text(
                        text       = video?.title ?: "",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color      = Color.White,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                }
            }

            // Bottom controls slide up + fade in.
            AnimatedVisibility(
                visible = controlsVisible,
                enter   = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec  = spring(dampingRatio = 0.8f, stiffness = 300f),
                ) + fadeIn(spring(stiffness = Spring.StiffnessLow)),
                exit    = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .systemBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                        .clip(ControlsShape)
                        .liquidGlass(alpha = 0.50f)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    val durationMs = duration.coerceAtLeast(1L)
                    val livePos    = if (isSeeking) seekPreview else currentPosition
                    val progress   = (livePos.toFloat() / durationMs).coerceIn(0f, 1f)

                    SmoothSeekBar(
                        progress     = progress,
                        onScrubStart = { isSeeking = true },
                        onScrub      = { f -> seekPreview = (f * durationMs).toLong() },
                        onScrubEnd   = { f ->
                            val target = (f * durationMs).toLong()
                            player.seekTo(target)
                            currentPosition = target
                            isSeeking = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text  = livePos.formatMs(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                        Spacer(Modifier.weight(1f))
                        PlayPauseButton(
                            isPlaying = isPlaying,
                            onClick   = { if (player.isPlaying) player.pause() else player.play() },
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text  = durationMs.formatMs(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.84f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "playpause-scale",
    )
    Box(
        modifier = Modifier
            .scale(scale)
            .size(60.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(
                interactionSource = interaction,
                indication        = null,
                onClick           = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Icon swap cross-fades.
        androidx.compose.animation.AnimatedContent(
            targetState   = isPlaying,
            transitionSpec = {
                (fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                    scaleIn(initialScale = 0.7f, animationSpec = spring(dampingRatio = 0.55f)))
                    .togetherWith(fadeOut(spring(stiffness = Spring.StiffnessMedium)))
            },
            label = "play-pause-icon",
        ) { playing ->
            Icon(
                imageVector        = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint               = Color.White,
                modifier           = Modifier.size(38.dp),
            )
        }
    }
}

@Composable
private fun GlassIconButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.82f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "glass-icon-scale",
    )
    Box(
        modifier = Modifier
            .scale(scale)
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .clickable(
                interactionSource = interaction,
                indication        = null,
                onClick           = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun Long.formatMs(): String {
    val s   = this / 1_000L
    val h   = s / 3600
    val m   = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
