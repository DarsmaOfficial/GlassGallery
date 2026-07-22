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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import android.app.Activity
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.darsma.glassgallery.data.PlaybackStore
import com.darsma.glassgallery.data.Video
import com.darsma.glassgallery.ui.components.BouncyIconButton
import com.darsma.glassgallery.ui.components.Motion
import com.darsma.glassgallery.ui.components.MediaDetailsSheet
import com.darsma.glassgallery.ui.components.MorphingPlayPauseButton
import com.darsma.glassgallery.ui.components.favoriteBurst
import com.darsma.glassgallery.ui.components.glassSheen
import com.darsma.glassgallery.ui.components.SmoothSeekBar
import com.darsma.glassgallery.ui.components.liquidGlass
import com.darsma.glassgallery.ui.components.opticalGlass
import com.darsma.glassgallery.ui.components.pressBounce
import com.darsma.glassgallery.ui.gallery.GalleryViewModel
import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

private val ControlsShape = RoundedCornerShape(26.dp)
private val ChipShape     = RoundedCornerShape(50)

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
    val playbackStore = remember { PlaybackStore(context) }

    val video: Video? by produceState<Video?>(null, videoId) {
        value = MediaStoreVideoSource(context).loadVideoById(videoId)
    }

    val favorites by galleryViewModel.favorites.collectAsState()
    val isFavorite = videoId in favorites

    val videoUri = remember(videoId) {
        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, videoId)
    }

    val player = remember {
        ExoPlayer.Builder(context).build().also { p ->
            p.setMediaItem(MediaItem.fromUri(videoUri))
            p.prepare()
            // Resume exactly where the user left off last time.
            val resumeAt = playbackStore.getPosition(videoId)
            if (resumeAt > 0L) p.seekTo(resumeAt)
            p.playWhenReady = true
        }
    }
    // Persist position when leaving the screen.
    DisposableEffect(player) {
        onDispose {
            playbackStore.savePosition(
                videoId    = videoId,
                positionMs = player.currentPosition,
                durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
            )
            player.release()
        }
    }

    var isPlaying       by remember { mutableStateOf(player.isPlaying) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration        by remember { mutableLongStateOf(0L) }
    var isSeeking       by remember { mutableStateOf(false) }
    var seekPreview     by remember { mutableLongStateOf(0L) }
    var speed           by rememberSaveable { mutableFloatStateOf(1.0f) }

    // Double-tap-to-seek feedback state.
    var seekSide   by remember { mutableStateOf(SeekSide.NONE) }
    var seekAmount by remember { mutableIntStateOf(0) }

    // Predictive-back progress: 0 = full player, 1 = fully "handed back" to grid.
    val backProgress = remember { Animatable(0f) }

    // As the user swipes back, shrink + fade the player toward the gallery.
    // Releasing past the threshold completes the pop; cancelling springs home.
    PredictiveBackHandler { progressFlow ->
        try {
            progressFlow.collect { backEvent ->
                backProgress.snapTo(backEvent.progress)
            }
            // Flow completed normally → gesture committed.
            onBack()
        } catch (e: CancellationException) {
            // Gesture cancelled → spring the player back to full size.
            backProgress.animateTo(0f, animationSpec = Motion.expressive())
        }
    }

    LaunchedEffect(speed) {
        player.playbackParameters = PlaybackParameters(speed)
    }

    LaunchedEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        player.addListener(listener)
        // Frame-synced position updates: the UI reads a fresh playback position
        // on every single rendered frame, so the seek bar glides instead of
        // stepping in 60 ms increments. The cross-screen MiniPlayer state only
        // needs ~5 Hz, so it is throttled separately.
        var lastStateSync = 0L
        while (true) {
            withFrameMillis { frameTimeMs ->
                if (!isSeeking) currentPosition = player.currentPosition
                duration = if (player.duration != C.TIME_UNSET) player.duration else 0L
                if (frameTimeMs - lastStateSync >= 200L) {
                    lastStateSync = frameTimeMs
                    val dur = if (player.duration != C.TIME_UNSET) player.duration.coerceAtLeast(1L) else 1L
                    galleryViewModel.updatePlaybackState(
                        progress  = (player.currentPosition.toFloat() / dur).coerceIn(0f, 1f),
                        isPlaying = player.isPlaying,
                    )
                }
            }
        }
    }

    var controlsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(220L); controlsVisible = true }

    var detailsVisible by remember { mutableStateOf(false) }

    // System-confirmed delete: MediaStore shows the OS dialog, and on OK we
    // prune the item from the gallery and leave the player.
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            galleryViewModel.removeFromList(setOf(videoId))
            onBack()
        }
    }

    // Auto-hide the chrome while playback runs, like a polished video player.
    // Pausing or scrubbing keeps the controls on screen; a single tap on the
    // video brings them back (or dismisses them early).
    LaunchedEffect(controlsVisible, isPlaying, isSeeking) {
        if (controlsVisible && isPlaying && !isSeeking) {
            delay(4_000L)
            controlsVisible = false
        }
    }

    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Live predictive-back transform: the whole player shrinks
                    // slightly and fades as the user swipes, previewing the
                    // return to the grid. Corner rounding only kicks in mid-swipe.
                    val p = backProgress.value
                    val s = 1f - 0.16f * p
                    scaleX = s
                    scaleY = s
                    alpha  = 1f - 0.30f * p
                    // Settle slightly downward as the gesture progresses — it
                    // previews the player returning home to the bottom bar.
                    translationY = 28.dp.toPx() * p
                    if (p > 0.001f) {
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(48f * p)
                        clip  = true
                    }
                }
                .background(Color.Black),
        ) {
            // ── Video surface — morphs to/from the bottom MiniPlayer ──────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .sharedBounds(
                        sharedContentState      = rememberSharedContentState("video-surface-$videoId"),
                        animatedVisibilityScope = animatedVisibilityScope,
                        boundsTransform         = { _, _ -> Motion.expressive() },
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap       = { controlsVisible = !controlsVisible },
                            onDoubleTap = { offset ->
                                val forward = offset.x > size.width / 2f
                                val step    = 10_000L
                                val target  = if (forward)
                                    (player.currentPosition + step)
                                        .coerceAtMost(player.duration.coerceAtLeast(0L))
                                else
                                    (player.currentPosition - step).coerceAtLeast(0L)
                                player.seekTo(target)
                                currentPosition = target
                                seekAmount = 10
                                seekSide   = if (forward) SeekSide.FORWARD else SeekSide.BACKWARD
                            },
                        )
                    },
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

            // Double-tap seek flourish — auto-clears after a beat.
            LaunchedEffect(seekSide, seekAmount) {
                if (seekSide != SeekSide.NONE) {
                    delay(620L)
                    seekSide = SeekSide.NONE
                }
            }
            SeekRippleOverlay(
                side     = seekSide,
                seconds  = seekAmount,
                modifier = Modifier.fillMaxSize(),
            )

            // ── Top bar ───────────────────────────────────────────────────
            AnimatedVisibility(
                visible  = controlsVisible,
                enter    = slideInVertically(initialOffsetY = { -it }, animationSpec = Motion.expressive()) +
                           fadeIn(Motion.standard()),
                exit     = slideOutVertically(targetOffsetY = { -it }, animationSpec = Motion.standard()) + fadeOut(),
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .systemBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .clip(ControlsShape)
                        .liquidGlass(alpha = 0.42f)
                        .opticalGlass(intensity = 0.76f)
                        .glassSheen()
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BouncyIconButton(onClick = onBack, size = 46.dp) {
                        Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White)
                    }
                    Spacer(Modifier.width(4.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp),
                    ) {
                        Text(
                            text       = video?.title ?: "",
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color      = Color.White,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis,
                        )
                        // File size — appears the moment metadata resolves.
                        AnimatedVisibility(
                            visible = video != null,
                            enter   = fadeIn(Motion.standard()),
                            exit    = fadeOut(),
                        ) {
                            Text(
                                text     = video?.readableSize ?: "",
                                fontSize = 11.sp,
                                color    = Color.White.copy(alpha = 0.55f),
                            )
                        }
                    }
                    // Favorite.
                    FavoriteButton(
                        isFavorite = isFavorite,
                        onToggle   = { galleryViewModel.toggleFavorite(videoId) },
                    )
                    Spacer(Modifier.width(2.dp))
                    // Share.
                    BouncyIconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "video/*"
                                putExtra(Intent.EXTRA_STREAM, videoUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share video"))
                        },
                        size = 46.dp,
                    ) {
                        Icon(Icons.Rounded.Share, "Share", tint = Color.White)
                    }
                    Spacer(Modifier.width(2.dp))
                    BouncyIconButton(
                        onClick = { detailsVisible = true },
                        size    = 46.dp,
                    ) {
                        Icon(Icons.Rounded.Info, "Details", tint = Color.White)
                    }
                    Spacer(Modifier.width(2.dp))
                    BouncyIconButton(
                        onClick = {
                            val pi = android.provider.MediaStore.createDeleteRequest(
                                context.contentResolver, listOf(videoUri)
                            )
                            deleteLauncher.launch(
                                IntentSenderRequest.Builder(pi.intentSender).build()
                            )
                        },
                        size = 46.dp,
                    ) {
                        Icon(
                            Icons.Rounded.Delete,
                            "Delete",
                            tint = Color(0xFFFF7A7A),
                        )
                    }
                }
            }

            // ── Bottom controls ───────────────────────────────────────────
            AnimatedVisibility(
                visible  = controlsVisible,
                enter    = slideInVertically(initialOffsetY = { it }, animationSpec = Motion.expressive()) +
                           fadeIn(Motion.standard()),
                exit     = slideOutVertically(targetOffsetY = { it }, animationSpec = Motion.standard()) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .systemBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 18.dp)
                        .clip(ControlsShape)
                        .liquidGlass(alpha = 0.50f)
                        .opticalGlass(intensity = 0.84f)
                        .glassSheen()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    val durationMs = duration.coerceAtLeast(1L)
                    val livePos    = if (isSeeking) seekPreview else currentPosition
                    val progress   = (livePos.toFloat() / durationMs).coerceIn(0f, 1f)

                    SmoothSeekBar(
                        progress     = progress,
                        isPlaying    = isPlaying,
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

                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text  = livePos.formatMs(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                        Spacer(Modifier.weight(1f))
                        MorphingPlayPauseButton(
                            isPlaying = isPlaying,
                            onClick   = { if (player.isPlaying) player.pause() else player.play() },
                            size      = 68.dp,
                            iconSize  = 38.dp,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text  = durationMs.formatMs(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── Info chips: size + speed ───────────────────────────
                    Row(
                        modifier          = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // File-size chip.
                        InfoChip(
                            label = "Size",
                            value = video?.readableSize ?: "—",
                        )
                        Spacer(Modifier.width(10.dp))
                        InfoChip(
                            label = "Length",
                            value = durationMs.formatMs(),
                        )
                        Spacer(Modifier.weight(1f))
                        // Speed cycling pill.
                        SpeedPill(
                            currentSpeed  = speed,
                            onSpeedChange = { speed = it },
                        )
                    }
                }
            }

            MediaDetailsSheet(
                visible   = detailsVisible,
                video     = video,
                onDismiss = { detailsVisible = false },
            )
        }
    }
}

// ── Info chip ─────────────────────────────────────────────────────────────────

@Composable
private fun InfoChip(label: String, value: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text     = label,
            fontSize = 10.sp,
            color    = Color.White.copy(alpha = 0.50f),
        )
        Spacer(Modifier.width(6.dp))
        AnimatedContent(
            targetState    = value,
            transitionSpec = { fadeIn(Motion.standard()) togetherWith fadeOut(Motion.snappy()) },
            label          = "info-chip-value",
        ) { v ->
            Text(
                text       = v,
                fontSize   = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color      = Color.White,
            )
        }
    }
}

// ── Favorite button ───────────────────────────────────────────────────────────

@Composable
private fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pop by androidx.compose.animation.core.animateFloatAsState(
        targetValue   = if (isFavorite) 1f else 0f,
        animationSpec = Motion.bouncy(),
        label         = "player-heart-pop",
    )
    Box(
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.78f, spec = Motion.snappy())
            .size(46.dp)
            .favoriteBurst(isFavorite)
            .clip(CircleShape)
            .background(
                if (isFavorite) Color(0xFFFF5C8A).copy(alpha = 0.16f)
                else Color.White.copy(alpha = 0.10f)
            )
            .clickable(
                interactionSource = interaction,
                indication        = null,
                onClick           = { runCatching { onToggle() } },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState    = isFavorite,
            transitionSpec = {
                scaleIn(Motion.bouncy()) + fadeIn(Motion.snappy()) togetherWith
                    scaleOut(Motion.snappy()) + fadeOut(Motion.snappy())
            },
            label = "player-heart-icon",
        ) { fav ->
            Icon(
                imageVector        = if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (fav) "Remove favorite" else "Add favorite",
                tint               = if (fav) Color(0xFFFF5C8A) else Color.White,
                modifier           = Modifier
                    .size(22.dp)
                    .graphicsLayer {
                        val s = 1f + 0.20f * pop
                        scaleX = s
                        scaleY = s
                    },
            )
        }
    }
}

// ── Speed cycling pill ────────────────────────────────────────────────────────

@Composable
private fun SpeedPill(currentSpeed: Float, onSpeedChange: (Float) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .pressBounce(interaction, pressedScale = 0.86f, spec = Motion.snappy())
            .clip(ChipShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.28f))
            .clickable(
                interactionSource = interaction,
                indication        = null,
            ) {
                val idx  = SPEED_STEPS.indexOf(currentSpeed).coerceAtLeast(0)
                onSpeedChange(SPEED_STEPS[(idx + 1) % SPEED_STEPS.size])
            }
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState    = currentSpeed.label(),
            transitionSpec = {
                (slideInVertically(Motion.expressive()) { it / 2 } + fadeIn(Motion.snappy()) +
                    scaleIn(Motion.bouncy(), initialScale = 0.7f)) togetherWith
                    (slideOutVertically(Motion.snappy()) { -it / 2 } + fadeOut(Motion.snappy()))
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

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun Long.formatMs(): String {
    val s   = this / 1_000L
    val h   = s / 3600
    val m   = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
