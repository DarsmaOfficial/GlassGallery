package com.darsma.glassgallery.ui.trim

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.ui.PlayerView
import com.darsma.glassgallery.data.MediaStoreVideoSource
import com.darsma.glassgallery.data.Video
import com.darsma.glassgallery.ui.components.BouncyIconButton
import com.darsma.glassgallery.ui.components.glassSheen
import com.darsma.glassgallery.ui.components.liquidGlass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToLong

private val TrimChromeShape = RoundedCornerShape(26.dp)
private const val MinimumTrimMs = 100L
private const val MinimumFractionGap = 0.000001f
private const val EditorOpenFailureMessage = "Couldn't open the editor for this video."

private sealed interface VideoLoadState {
    data object Loading : VideoLoadState
    data class Ready(val video: Video) : VideoLoadState
    data class Failed(val message: String) : VideoLoadState
}

private sealed interface TrimExportState {
    data object Idle : TrimExportState
    data class Exporting(val progress: Int?) : TrimExportState
    data object Completed : TrimExportState
    data class Failed(val message: String) : TrimExportState
}

private data class TrimEditorSetup(
    val durationMs: Long,
    val minimumFraction: Float,
    val player: ExoPlayer,
)

private data class TrimFractions(
    val start: Float,
    val end: Float,
    val preview: Float,
)

@Composable
fun VideoTrimScreen(
    videoId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val loadState by produceState<VideoLoadState>(VideoLoadState.Loading, videoId) {
        value = runCatching { MediaStoreVideoSource(context).loadVideoById(videoId) }
            .fold(
                onSuccess = { video ->
                    video?.let { VideoLoadState.Ready(it) }
                        ?: VideoLoadState.Failed("Video is no longer available.")
                },
                onFailure = { error ->
                    VideoLoadState.Failed(error.userMessage("Could not open this video."))
                },
            )
    }

    when (val state = loadState) {
        VideoLoadState.Loading -> TrimMessageScreen(
            onBack = onBack,
            message = "Loading video…",
            loading = true,
        )
        is VideoLoadState.Failed -> TrimMessageScreen(
            onBack = onBack,
            message = state.message,
            loading = false,
        )
        is VideoLoadState.Ready -> VideoTrimEditor(
            video = state.video,
            onBack = onBack,
            onSaved = onSaved,
        )
    }
}

@Composable
private fun TrimMessageScreen(
    onBack: () -> Unit,
    message: String,
    loading: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding(),
    ) {
        BouncyIconButton(
            onClick = onBack,
            modifier = Modifier.padding(16.dp),
            size = 46.dp,
        ) {
            Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White)
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp),
                )
                Spacer(Modifier.height(14.dp))
            }
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.82f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 28.dp),
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoTrimEditor(
    video: Video,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()
    val setupResult = remember(appContext, video.id, video.uri, video.duration) {
        createTrimEditorSetup(appContext, video)
    }
    val setup = setupResult.getOrNull()
    if (setup == null) {
        TrimMessageScreen(
            onBack = onBack,
            message = EditorOpenFailureMessage,
            loading = false,
        )
        return
    }
    val durationMs = setup.durationMs
    val minimumFraction = setup.minimumFraction
    val player = setup.player

    var startFraction by remember(video.id) { mutableFloatStateOf(0f) }
    var endFraction by remember(video.id) { mutableFloatStateOf(1f) }
    var previewFraction by remember(video.id) { mutableFloatStateOf(0f) }
    var isPlaying by remember(video.id) { mutableStateOf(false) }
    var editorFailure by remember(video.id) { mutableStateOf<String?>(null) }
    var exportState by remember(video.id) { mutableStateOf<TrimExportState>(TrimExportState.Idle) }
    var exportJob by remember(video.id) { mutableStateOf<Job?>(null) }
    var activeTempFile by remember(video.id) { mutableStateOf<File?>(null) }

    val fractions = normalizeTrimFractions(
        start = startFraction,
        end = endFraction,
        preview = previewFraction,
        minimumGap = minimumFraction,
    )
    val startMs = fractionToPositionMs(fractions.start, durationMs)
        .coerceIn(0L, durationMs - 1L)
    val endMs = fractionToPositionMs(fractions.end, durationMs)
        .coerceIn(startMs + 1L, durationMs)
    val previewMs = fractionToPositionMs(fractions.preview, durationMs)
        .coerceIn(startMs, endMs)
    val isExporting = exportState is TrimExportState.Exporting

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayerError(error: PlaybackException) {
                editorFailure = EditorOpenFailureMessage
            }
        }
        val listenerAttached = runCatching { player.addListener(listener) }.isSuccess
        val existingPlayerError = runCatching { player.playerError }.getOrNull()
        if (!listenerAttached || existingPlayerError != null) {
            editorFailure = EditorOpenFailureMessage
        }
        onDispose {
            runCatching { exportJob?.cancel() }
            runCatching { activeTempFile?.delete() }
            if (listenerAttached) {
                runCatching { player.removeListener(listener) }
            }
            runCatching { player.release() }
        }
    }

    if (editorFailure != null) {
        TrimMessageScreen(
            onBack = onBack,
            message = editorFailure ?: EditorOpenFailureMessage,
            loading = false,
        )
        return
    }

    LaunchedEffect(player, startMs, endMs, fractions.start, fractions.end) {
        while (true) {
            val updateSucceeded = runCatching {
                val current = player.currentPosition.coerceAtLeast(0L)
                if (player.isPlaying && current >= endMs) {
                    player.pause()
                    player.seekTo(startMs)
                    previewFraction = fractions.start
                } else if (player.isPlaying) {
                    previewFraction = boundedFraction(
                        value = current.toDouble().div(durationMs.toDouble()).toFloat(),
                        minimum = fractions.start,
                        maximum = fractions.end,
                    )
                }
            }.isSuccess
            if (!updateSucceeded) {
                editorFailure = EditorOpenFailureMessage
                return@LaunchedEffect
            }
            delay(80L)
        }
    }

    fun cancelAndGoBack() {
        exportJob?.cancel()
        onBack()
    }

    BackHandler(onBack = ::cancelAndGoBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TrimChromeShape)
                .liquidGlass(alpha = 0.42f)
                .glassSheen()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BouncyIconButton(
                onClick = ::cancelAndGoBack,
                size = 44.dp,
            ) {
                Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    text = "Trim video",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = video.title,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF111111)),
        ) {
            AndroidView(
                factory = { viewContext ->
                    runCatching<android.view.View> {
                        PlayerView(viewContext).apply {
                            this.player = player
                            useController = false
                        }
                    }.getOrElse {
                        scope.launch {
                            editorFailure = EditorOpenFailureMessage
                        }
                        FrameLayout(viewContext)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            BouncyIconButton(
                onClick = {
                    runCatching {
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            if (player.currentPosition !in startMs until endMs) {
                                player.seekTo(startMs)
                                previewFraction = fractions.start
                            }
                            player.play()
                        }
                    }.onFailure {
                        editorFailure = EditorOpenFailureMessage
                    }
                },
                modifier = Modifier.align(Alignment.Center),
                size = 66.dp,
                background = Color.Black.copy(alpha = 0.52f),
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Pause preview" else "Play preview",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TrimChromeShape)
                .liquidGlass(alpha = 0.50f)
                .glassSheen()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TimeLabel("Start", startMs)
                TimeLabel("End", endMs)
            }

            RangeSlider(
                value = fractions.start..fractions.end,
                onValueChange = { requested ->
                    val requestedStart = unitFractionOrFallback(
                        requested.start,
                        fractions.start,
                    )
                    val requestedEnd = unitFractionOrFallback(
                        requested.endInclusive,
                        fractions.end,
                    )
                    val startMoved =
                        abs(requestedStart - fractions.start) >=
                            abs(requestedEnd - fractions.end)
                    val updated = normalizeTrimFractions(
                        start = requestedStart,
                        end = requestedEnd,
                        preview = if (startMoved) requestedStart else requestedEnd,
                        minimumGap = minimumFraction,
                        startMoved = startMoved,
                    )
                    startFraction = updated.start
                    endFraction = updated.end
                    previewFraction = updated.preview
                    runCatching {
                        player.pause()
                        player.seekTo(fractionToPositionMs(updated.preview, durationMs))
                    }.onFailure {
                        editorFailure = EditorOpenFailureMessage
                    }
                },
                valueRange = 0f..1f,
                enabled = !isExporting,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Preview · ${previewMs.formatMs()}",
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.labelMedium,
            )
            Slider(
                value = fractions.preview,
                onValueChange = { fraction ->
                    val updatedPreview = boundedFraction(
                        value = fraction,
                        minimum = fractions.start,
                        maximum = fractions.end,
                    )
                    previewFraction = updatedPreview
                    runCatching {
                        player.pause()
                        player.seekTo(fractionToPositionMs(updatedPreview, durationMs))
                    }.onFailure {
                        editorFailure = EditorOpenFailureMessage
                    }
                },
                valueRange = fractions.start..fractions.end,
                enabled = !isExporting,
                modifier = Modifier.fillMaxWidth(),
            )

            when (val state = exportState) {
                TrimExportState.Idle -> Unit
                is TrimExportState.Exporting -> {
                    val progress = state.progress
                    Text(
                        text = progress?.let { "Exporting… $it%" } ?: "Preparing export…",
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.12f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((progress ?: 0).coerceIn(0, 100) / 100f)
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
                TrimExportState.Completed -> {
                    Text(
                        text = "Trimmed copy saved",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                is TrimExportState.Failed -> {
                    Text(
                        text = state.message,
                        color = Color(0xFFFF8A8A),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            if (isExporting) {
                TextButton(
                    onClick = { exportJob?.cancel() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel export")
                }
            } else {
                Button(
                    onClick = {
                        player.pause()
                        exportState = TrimExportState.Exporting(progress = null)
                        exportJob = scope.launch {
                            val tempFile = File(
                                context.cacheDir,
                                "GlassTrim_export_${System.currentTimeMillis()}.mp4",
                            )
                            activeTempFile = tempFile
                            try {
                                val clippingConfiguration =
                                    MediaItem.ClippingConfiguration.Builder()
                                        .setStartPositionMs(startMs)
                                        .setEndPositionMs(endMs)
                                        .build()
                                val mediaItem = MediaItem.Builder()
                                    .setUri(video.uri)
                                    .setClippingConfiguration(clippingConfiguration)
                                    .build()
                                val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()

                                runTransformerExport(
                                    context = context,
                                    editedMediaItem = editedMediaItem,
                                    outputFile = tempFile,
                                    onProgress = { progress ->
                                        exportState = TrimExportState.Exporting(progress)
                                    },
                                )
                                withContext(Dispatchers.IO) {
                                    publishTrimmedVideo(context, tempFile)
                                }
                                exportState = TrimExportState.Completed
                                delay(650L)
                                onSaved()
                            } catch (cancelled: CancellationException) {
                                exportState = TrimExportState.Failed("Export cancelled.")
                                throw cancelled
                            } catch (error: Throwable) {
                                exportState = TrimExportState.Failed(
                                    error.userMessage("Could not save the trimmed copy.")
                                )
                            } finally {
                                tempFile.delete()
                                activeTempFile = null
                                exportJob = null
                            }
                        }
                    },
                    enabled = endMs > startMs,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.ContentCut, contentDescription = null)
                    Text(
                        text = "Save trimmed copy",
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

private fun createTrimEditorSetup(
    context: Context,
    video: Video,
): Result<TrimEditorSetup> {
    var player: ExoPlayer? = null
    return runCatching {
        val durationMs = video.duration.takeIf { it > 0L } ?: 1L
        val requestedMinimum = (
            minOf(MinimumTrimMs, durationMs).toDouble() / durationMs.toDouble()
        ).toFloat()
        val minimumFraction = requestedMinimum
            .takeIf(Float::isFinite)
            ?.coerceIn(MinimumFractionGap, 1f - MinimumFractionGap)
            ?: MinimumFractionGap

        val preparedPlayer = ExoPlayer.Builder(context).build()
        player = preparedPlayer
        preparedPlayer.setMediaItem(MediaItem.fromUri(video.uri))
        preparedPlayer.prepare()

        TrimEditorSetup(
            durationMs = durationMs,
            minimumFraction = minimumFraction,
            player = preparedPlayer,
        )
    }.onFailure {
        runCatching { player?.release() }
    }
}

private fun normalizeTrimFractions(
    start: Float,
    end: Float,
    preview: Float,
    minimumGap: Float,
    startMoved: Boolean? = null,
): TrimFractions {
    val gap = minimumGap
        .takeIf(Float::isFinite)
        ?.coerceIn(MinimumFractionGap, 1f - MinimumFractionGap)
        ?: MinimumFractionGap
    var safeStart = unitFractionOrFallback(start, 0f)
    var safeEnd = unitFractionOrFallback(end, 1f)

    if (safeStart >= safeEnd || safeEnd - safeStart < gap) {
        when (startMoved) {
            true -> {
                safeEnd = safeEnd.coerceIn(gap, 1f)
                safeStart = safeEnd - gap
            }
            false -> {
                safeStart = safeStart.coerceIn(0f, 1f - gap)
                safeEnd = safeStart + gap
            }
            null -> {
                if (safeStart < safeEnd) {
                    if (safeStart <= 1f - gap) {
                        safeEnd = safeStart + gap
                    } else {
                        safeStart = 1f - gap
                        safeEnd = 1f
                    }
                } else {
                    safeStart = 0f
                    safeEnd = 1f
                }
            }
        }
    }

    if (!safeStart.isFinite() || !safeEnd.isFinite() || safeStart >= safeEnd) {
        safeStart = 0f
        safeEnd = 1f
    }
    return TrimFractions(
        start = safeStart,
        end = safeEnd,
        preview = boundedFraction(preview, safeStart, safeEnd),
    )
}

private fun unitFractionOrFallback(value: Float, fallback: Float): Float {
    val finiteValue = value.takeIf(Float::isFinite)
        ?: fallback.takeIf(Float::isFinite)
        ?: 0f
    return finiteValue.coerceIn(0f, 1f)
}

private fun boundedFraction(value: Float, minimum: Float, maximum: Float): Float {
    val safeMinimum = unitFractionOrFallback(minimum, 0f)
    val safeMaximum = unitFractionOrFallback(maximum, 1f)
    if (safeMinimum >= safeMaximum) {
        return safeMinimum
    }
    return unitFractionOrFallback(value, safeMinimum).coerceIn(safeMinimum, safeMaximum)
}

private fun fractionToPositionMs(fraction: Float, durationMs: Long): Long {
    val safeDuration = durationMs.coerceAtLeast(1L)
    return (unitFractionOrFallback(fraction, 0f).toDouble() * safeDuration.toDouble())
        .roundToLong()
        .coerceIn(0L, safeDuration)
}

@Composable
private fun TimeLabel(label: String, timeMs: Long) {
    Column {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.50f),
            fontSize = 10.sp,
        )
        Text(
            text = timeMs.formatMs(),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@OptIn(UnstableApi::class)
private suspend fun runTransformerExport(
    context: Context,
    editedMediaItem: EditedMediaItem,
    outputFile: File,
    onProgress: (Int) -> Unit,
) = withContext(Dispatchers.Main.immediate) {
    suspendCancellableCoroutine { continuation ->
        val handler = Handler(Looper.getMainLooper())
        var progressRunnable: Runnable? = null
        lateinit var transformer: Transformer

        fun stopPolling() {
            progressRunnable?.let(handler::removeCallbacks)
        }

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                stopPolling()
                onProgress(100)
                transformer.removeAllListeners()
                if (continuation.isActive) {
                    continuation.resumeWith(Result.success(Unit))
                }
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                stopPolling()
                transformer.removeAllListeners()
                if (continuation.isActive) {
                    continuation.resumeWith(Result.failure(exportException))
                }
            }
        }

        transformer = Transformer.Builder(context)
            .addListener(listener)
            .build()

        continuation.invokeOnCancellation {
            handler.post {
                stopPolling()
                runCatching { transformer.cancel() }
                runCatching { transformer.removeAllListeners() }
                outputFile.delete()
            }
        }

        try {
            transformer.start(editedMediaItem, outputFile.absolutePath)
            progressRunnable = object : Runnable {
                private val holder = ProgressHolder()

                override fun run() {
                    if (!continuation.isActive) return
                    runCatching {
                        if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                            onProgress(holder.progress.coerceIn(0, 100))
                        }
                    }
                    handler.postDelayed(this, 250L)
                }
            }.also(handler::post)
        } catch (error: Throwable) {
            stopPolling()
            runCatching { transformer.removeAllListeners() }
            if (continuation.isActive) {
                continuation.resumeWith(Result.failure(error))
            }
        }
    }
}

private fun publishTrimmedVideo(context: Context, outputFile: File) {
    check(outputFile.isFile && outputFile.length() > 0L) {
        "Transformer produced no output."
    }

    val resolver = context.contentResolver
    val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val values = ContentValues().apply {
        put(
            MediaStore.Video.Media.DISPLAY_NAME,
            "GlassTrim_${System.currentTimeMillis()}.mp4",
        )
        put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
        put(
            MediaStore.Video.Media.RELATIVE_PATH,
            "${Environment.DIRECTORY_MOVIES}/Glass Gallery",
        )
        put(MediaStore.Video.Media.IS_PENDING, 1)
    }

    val target = resolver.insert(collection, values)
        ?: error("MediaStore could not create the trimmed copy.")
    try {
        resolver.openOutputStream(target, "w")?.use { destination ->
            outputFile.inputStream().use { source ->
                source.copyTo(destination)
            }
        } ?: error("MediaStore could not open the trimmed copy.")

        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        check(resolver.update(target, values, null, null) == 1) {
            "MediaStore could not publish the trimmed copy."
        }
    } catch (error: Throwable) {
        runCatching { resolver.delete(target, null, null) }
        throw error
    }
}

private fun Throwable.userMessage(fallback: String): String {
    val detail = message?.trim().orEmpty()
    return if (detail.isBlank()) fallback else "$fallback $detail"
}

private fun Long.formatMs(): String {
    val seconds = this / 1_000L
    val hours = seconds / 3_600L
    val minutes = (seconds % 3_600L) / 60L
    val remainingSeconds = seconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, remainingSeconds)
    } else {
        "%d:%02d".format(minutes, remainingSeconds)
    }
}
