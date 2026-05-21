@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.darsma.glassgallery.ui.player

import android.content.ContentUris
import android.content.Intent
import android.provider.MediaStore
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.darsma.glassgallery.data.MediaStoreVideoSource
import com.darsma.glassgallery.ui.gallery.GalleryViewModel
import com.darsma.glassgallery.data.Video
import com.darsma.glassgallery.ui.components.SmoothSeekBar
import com.darsma.glassgallery.ui.components.liquidGlass
import kotlinx.coroutines.delay

private val ControlsShape = RoundedCornerShape(26.dp)

// Available playback speeds cycling order
private val SPEED_STEPS = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
private fun Float.label(): String = when (this) {
    0.5f  -> "0.5×"
    1.0f  -> "1×"
    1.25f -> "1.25×"
    1.5f  -> "1.5×"
    2.0f  -> "2×"
    else  -> "${this}×"
}

@Composable
fun PlayerScreen(
    videoId: Long,
    galleryViewModel: GalleryViewModel,
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

    // Speed — persists across config changes
    var speed by rememberSaveable { mutableFloatStateOf(1.0f) }

    // Apply speed whenever it changes
    LaunchedEffect(speed) {
        player.playbackParameters = PlaybackParameters(speed)
    }

    // Listener for instant play/pause + progress polling at 60 fps intervals
    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        player.addListener(listener)
        while (true) {
            if (!isSeeking) currentPosition = player.currentPosition
            duration = if (player.duration != C.TIME_UNSET) player.duration else 0L
            // Push state to ViewModel so MiniPlayer progress bar stays accurate
            val dur = if (player.duration != C.TIME_UNSET) player.duration.coerceAtLeast(1L) else 1L
            galleryViewModel.updatePlaybackState(
                progress  = (player.currentPosition.toFloat() / dur).coerceIn(0f, 1f),
                isPlaying = player.isPlaying,
            )
            delay(60L)
        }
    }

    // Controls animate in shortly after morph settles
    var controlsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(200L); controlsVisible = true }

    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            // ── Video surface ─────────────────────────────────────────────
            // Key matches gallery VideoCard AND MiniPlayer capsule so that
            // tapping a thumbnail OR re-opening from MiniPlayer both morph correctly.
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

            // ── Top bar ───────────────────────────────────────────────────
            AnimatedVisibility(
                visible  = controlsVisible,
                enter    = slideInVertically(initialOffsetY = { -it }, animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)) +
                           fadeIn(spring(stiffness = Spring.StiffnessLow)),
                exit     = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
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
                    // Back
                    GlassIconButton(onClick = onBack, size = 46.dp) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Spacer(Modifier.width(4.dp))
                    // Title
                    Text(
                        text       = video?.title ?: "",
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color      = Color.White,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                        modifier   = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    // Share
                    GlassIconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type  = "video/*"
                                putExtra(Intent.EXTRA_STREAM, videoUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share video"))
                        },
                        size = 46.dp,
                    ) {
                        Icon(Icons.Filled.Share, "Share", tint = Color.White)
                    }
                }
            }

            // ── Bottom controls ───────────────────────────────────────────
            AnimatedVisibility(
                visible  = controlsVisible,
                enter    = slideInVertically(initialOffsetY = { it }, animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f)) +
                           fadeIn(spring(stiffness = Spring.StiffnessLow)),
                exit     = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
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
                            val t = (f * durationMs).toLong()
                            player.seekTo(t)
                            currentPosition = t
                            isSeeking = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(6.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Elapsed time
                        Text(
                            text  = livePos.formatMs(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                        Spacer(Modifier.weight(1f))

                        // Play / Pause
                        PlayPauseButton(
                            isPlaying = isPlaying,
                            onClick   = { if (player.isPlaying) player.pause() else player.play() },
                        )

                        Spacer(Modifier.weight(1f))
                        // Duration
                        Text(
                            text  = durationMs.formatMs(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── Speed pill ─────────────────────────────────────────
                    Row(
                        modifier         = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text  = "Speed",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f),
                        )
                        Spacer(Modifier.width(10.dp))
                        SpeedPill(
                            currentSpeed = speed,
                            onSpeedChange = { newSpeed ->
                                speed = newSpeed
                            },
                        )
                    }
                }
            }
        }
    }
}

// ── Speed cycling pill ────────────────────────────────────────────────────────

@Composable
private fun SpeedPill(
    currentSpeed: Float,
    onSpeedChange: (Float) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pillScale by animateFloatAsState(
        targetValue   = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label         = "speed-pill-scale",
    )

    Box(
        modifier = Modifier
            .scale(pillScale)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            .clickable(
                interactionSource = interaction,
                indication        = null,
            ) {
                val idx  = SPEED_STEPS.indexOf(currentSpeed).coerceAtLeast(0)
                val next = SPEED_STEPS[(idx + 1) % SPEED_STEPS.size]
                onSpeedChange(next)
            }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState  = currentSpeed.label(),
            transitionSpec = {
                val enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                    scaleIn(initialScale = 0.6f, animationSpec = spring(dampingRatio = 0.6f))
                val exit  = fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                    scaleOut(targetScale = 0.6f, animationSpec = spring(dampingRatio = 0.6f))
                enter togetherWith exit
            },
            label = "speed-label",
        ) { label ->
            Text(
                text       = label,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ── Play / Pause button ───────────────────────────────────────────────────────

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
        label         = "playpause-scale",
    )
    Box(
        modifier = Modifier
            .scale(scale)
            .size(62.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(
                interactionSource = interaction,
                indication        = null,
                onClick           = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState  = isPlaying,
            transitionSpec = {
                val enter = fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                    scaleIn(initialScale = 0.7f, animationSpec = spring(dampingRatio = 0.55f))
                val exit  = fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                    scaleOut(targetScale = 0.7f, animationSpec = spring(dampingRatio = 0.55f))
                enter togetherWith exit
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

// ── Glass icon button ─────────────────────────────────────────────────────────

@Composable
private fun GlassIconButton(
    onClick: () -> Unit,
    size: Dp,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (pressed) 0.82f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label         = "icon-btn-scale",
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

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun Long.formatMs(): String {
    val s   = this / 1_000L
    val h   = s / 3600
    val m   = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
