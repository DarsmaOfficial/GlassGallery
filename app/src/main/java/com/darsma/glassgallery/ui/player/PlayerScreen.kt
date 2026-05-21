@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.darsma.glassgallery.ui.player

import android.content.ContentUris
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.darsma.glassgallery.data.MediaStoreVideoSource
import com.darsma.glassgallery.data.Video
import com.darsma.glassgallery.ui.components.MorphShape
import com.darsma.glassgallery.ui.components.liquidGlass
import com.darsma.glassgallery.ui.components.playerMorph
import kotlinx.coroutines.delay

private val ControlsShape = RoundedCornerShape(20.dp)

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

    LaunchedEffect(player) {
        while (true) {
            isPlaying = player.isPlaying
            if (!isSeeking) currentPosition = player.currentPosition
            duration = if (player.duration != C.TIME_UNSET) player.duration else 0L
            delay(200L)
        }
    }

    with(sharedTransitionScope) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .sharedBounds(
                    sharedContentState = rememberSharedContentState("now-bar-$videoId"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = { _, _ ->
                        spring(dampingRatio = 0.8f, stiffness = 380f)
                    },
                    clipInOverlayDuringTransition = SharedTransitionScope.OverlayClip(
                        MorphShape(morph = playerMorph, progress = 1f)
                    ),
                ),
        ) {
            // Video surface via AndroidView + PlayerView (useController=false)
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            // Top bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .systemBarsPadding()
                    .padding(8.dp)
                    .liquidGlass()
                    .background(Color.Black.copy(alpha = 0.30f), ControlsShape),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint               = Color.White,
                        )
                    }
                    Text(
                        text     = video?.title ?: "",
                        style    = MaterialTheme.typography.titleMedium,
                        color    = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                }
            }

            // Bottom controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .systemBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .liquidGlass()
                    .background(Color.Black.copy(alpha = 0.35f), ControlsShape)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                val durationMs = duration.coerceAtLeast(1L)
                val progress   = (currentPosition.toFloat() / durationMs).coerceIn(0f, 1f)

                Slider(
                    value = progress,
                    onValueChange = { fraction ->
                        isSeeking = true
                        currentPosition = (fraction * durationMs).toLong()
                    },
                    onValueChangeFinished = {
                        player.seekTo(currentPosition)
                        isSeeking = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor         = MaterialTheme.colorScheme.primary,
                        activeTrackColor   = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.30f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text  = currentPosition.formatMs(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.80f),
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick  = { if (player.isPlaying) player.pause() else player.play() },
                        modifier = Modifier.size(52.dp),
                    ) {
                        Icon(
                            imageVector        = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint               = Color.White,
                            modifier           = Modifier.size(36.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        text  = duration.formatMs(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.80f),
                    )
                }
            }
        }
    }
}

private fun Long.formatMs(): String {
    val s = this / 1_000L
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
