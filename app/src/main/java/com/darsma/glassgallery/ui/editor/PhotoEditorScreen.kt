package com.darsma.glassgallery.ui.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darsma.glassgallery.ui.components.BouncyIconButton
import com.darsma.glassgallery.ui.components.LiquidTabBar
import com.darsma.glassgallery.ui.components.Motion
import com.darsma.glassgallery.ui.components.glassSheen
import com.darsma.glassgallery.ui.components.liquidGlass
import com.darsma.glassgallery.ui.components.liquidGlassBorder
import com.darsma.glassgallery.ui.components.pressBounce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

private val ChromeShape = RoundedCornerShape(26.dp)
private val PillShape   = RoundedCornerShape(50)
private const val EditorHistoryLimit = 30

private data class EditorSnapshot(
    val rotationSteps: Float,
    val flippedH: Boolean,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val zoom: Float,
    val panX: Float,
    val panY: Float,
)

/**
 * Basic non-destructive photo editor. Two liquid-morphing modes:
 *  · Crop  — zoom/pan the photo inside a fixed frame plus 90° rotate & flip;
 *            the visible viewport becomes the crop.
 *  · Adjust — brightness / contrast / saturation ColorMatrix sliders.
 * Saving renders everything onto a Bitmap and inserts a COPY via MediaStore —
 * the original is never touched. 100% framework APIs.
 */
@Composable
fun PhotoEditorScreen(
    photoId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val photoUri = remember(photoId) {
        android.content.ContentUris.withAppendedId(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, photoId
        )
    }

    // Downsampled working bitmap — big enough to look sharp, small enough to fly.
    val bitmap by produceState<Bitmap?>(null, photoId) {
        value = withContext(Dispatchers.IO) { loadScaledBitmap(context, photoUri, 2048) }
    }

    // ── Edit state ──────────────────────────────────────────────────────────
    var mode by remember { mutableStateOf(0) }              // 0 = Crop, 1 = Adjust
    var rotationSteps by remember { mutableFloatStateOf(0f) } // 90° increments
    var flippedH by remember { mutableStateOf(false) }
    var brightness by remember { mutableFloatStateOf(0f) }  // -1..1
    var contrast   by remember { mutableFloatStateOf(0f) }  // -1..1
    var saturation by remember { mutableFloatStateOf(0f) }  // -1..1
    var saving by remember { mutableStateOf(false) }
    var comparingOriginal by remember { mutableStateOf(false) }
    var applyingHistory by remember { mutableStateOf(false) }
    var undoHistory by remember(photoId) { mutableStateOf<List<EditorSnapshot>>(emptyList()) }
    var redoHistory by remember(photoId) { mutableStateOf<List<EditorSnapshot>>(emptyList()) }
    var adjustmentGestureStart by remember { mutableStateOf<EditorSnapshot?>(null) }

    // Crop viewport: zoom + pan inside the frame.
    val zoom    = remember { Animatable(1f) }
    val panX    = remember { Animatable(0f) }
    val panY    = remember { Animatable(0f) }
    var frameSize by remember { mutableStateOf(IntSize.Zero) }

    fun snapshot() = EditorSnapshot(
        rotationSteps = rotationSteps,
        flippedH = flippedH,
        brightness = brightness,
        contrast = contrast,
        saturation = saturation,
        zoom = zoom.value,
        panX = panX.value,
        panY = panY.value,
    )

    fun rememberChange(before: EditorSnapshot) {
        if (applyingHistory) return
        val after = snapshot()
        if (after == before) return
        undoHistory = (undoHistory + before).takeLast(EditorHistoryLimit)
        redoHistory = emptyList()
    }

    fun applySnapshot(target: EditorSnapshot) {
        if (applyingHistory) return
        applyingHistory = true
        rotationSteps = target.rotationSteps
        flippedH = target.flippedH
        brightness = target.brightness
        contrast = target.contrast
        saturation = target.saturation
        scope.launch {
            try {
                zoom.snapTo(target.zoom.coerceIn(1f, 5f))
                panX.snapTo(target.panX)
                panY.snapTo(target.panY)
            } finally {
                applyingHistory = false
            }
        }
    }

    fun undo() {
        if (applyingHistory) return
        val target = undoHistory.lastOrNull() ?: return
        val current = snapshot()
        undoHistory = undoHistory.dropLast(1)
        redoHistory = (redoHistory + current).takeLast(EditorHistoryLimit)
        applySnapshot(target)
    }

    fun redo() {
        if (applyingHistory) return
        val target = redoHistory.lastOrNull() ?: return
        val current = snapshot()
        redoHistory = redoHistory.dropLast(1)
        undoHistory = (undoHistory + current).takeLast(EditorHistoryLimit)
        applySnapshot(target)
    }

    val currentSnapshot = rememberUpdatedState(snapshot())
    val rememberChangeUpdated = rememberUpdatedState<(EditorSnapshot) -> Unit> { before ->
        rememberChange(before)
    }

    val rotationAnim by animateFloatAsState(
        targetValue   = rotationSteps * 90f,
        animationSpec = Motion.expressive(),
        label         = "editor-rotation",
    )
    val flipAnim by animateFloatAsState(
        targetValue   = if (flippedH) -1f else 1f,
        animationSpec = Motion.expressive(),
        label         = "editor-flip",
    )

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scope.launch {
            val z = (zoom.value * zoomChange).coerceIn(1f, 5f)
            zoom.snapTo(z)
            val maxX = frameSize.width  * (z - 1f) / 2f
            val maxY = frameSize.height * (z - 1f) / 2f
            panX.snapTo((panX.value + panChange.x).coerceIn(-maxX, maxX))
            panY.snapTo((panY.value + panChange.y).coerceIn(-maxY, maxY))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // ── Photo preview: live ColorMatrix via ColorFilter ────────────────
        val preview = bitmap
        if (preview == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 116.dp, bottom = 276.dp)
                    .onSizeChanged { frameSize = it }
                    .clip(RoundedCornerShape(18.dp))
                    .pointerInput(mode, photoId) {
                        if (mode == 0) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                val before = currentSnapshot.value
                                do {
                                    val event = awaitPointerEvent()
                                } while (event.changes.any { it.pressed })
                                rememberChangeUpdated.value(before)
                            }
                        }
                    }
                    .transformable(transformState, enabled = mode == 0),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap             = preview.asImageBitmap(),
                    contentDescription = "Edited photo",
                    contentScale       = ContentScale.Fit,
                    colorFilter        = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                        composeColorMatrix(
                            if (comparingOriginal) 0f else brightness,
                            if (comparingOriginal) 0f else contrast,
                            if (comparingOriginal) 0f else saturation,
                        )
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = if (comparingOriginal) 1f else zoom.value * flipAnim
                            scaleY = if (comparingOriginal) 1f else zoom.value
                            translationX = if (comparingOriginal) 0f else panX.value
                            translationY = if (comparingOriginal) 0f else panY.value
                            rotationZ = if (comparingOriginal) 0f else rotationAnim
                        },
                )
                // Crop frame lines — visible only in crop mode.
                AnimatedVisibility(
                    visible = mode == 0,
                    enter   = fadeIn(Motion.standard()),
                    exit    = fadeOut(Motion.snappy()),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .liquidGlassBorder(RoundedCornerShape(18.dp))
                    )
                }
            }
        }

        // ── Top chrome: cancel / save ───────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clip(ChromeShape)
                .liquidGlass(alpha = 0.42f)
                .glassSheen()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BouncyIconButton(onClick = onBack, size = 42.dp) {
                Icon(Icons.Rounded.Close, "Cancel", tint = Color.White)
            }
            Text(
                text       = "Edit",
                fontSize   = 17.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
                modifier   = Modifier.weight(1f).padding(start = 8.dp),
            )
            BouncyIconButton(
                onClick = { if (undoHistory.isNotEmpty()) undo() },
                size = 38.dp,
                background = Color.White.copy(alpha = if (undoHistory.isNotEmpty()) 0.10f else 0.04f),
            ) {
                Icon(
                    Icons.Rounded.Undo,
                    "Undo",
                    tint = Color.White.copy(alpha = if (undoHistory.isNotEmpty()) 0.92f else 0.28f),
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            BouncyIconButton(
                onClick = { if (redoHistory.isNotEmpty()) redo() },
                size = 38.dp,
                background = Color.White.copy(alpha = if (redoHistory.isNotEmpty()) 0.10f else 0.04f),
            ) {
                Icon(
                    Icons.Rounded.Redo,
                    "Redo",
                    tint = Color.White.copy(alpha = if (redoHistory.isNotEmpty()) 0.92f else 0.28f),
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            AnimatedContent(
                targetState    = saving,
                transitionSpec = {
                    scaleIn(Motion.bouncy()) + fadeIn(Motion.snappy()) togetherWith
                        scaleOut(Motion.snappy()) + fadeOut(Motion.snappy())
                },
                label = "save-btn",
            ) { busy ->
                if (busy) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color       = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp,
                            modifier    = Modifier.size(24.dp),
                        )
                    }
                } else {
                    BouncyIconButton(
                        onClick    = {
                            val src = bitmap ?: return@BouncyIconButton
                            saving = true
                            scope.launch {
                                val ok = withContext(Dispatchers.IO) {
                                    runCatching {
                                        saveEditedCopy(
                                            context, src,
                                            rotationSteps.roundToInt(), flippedH,
                                            zoom.value, panX.value, panY.value, frameSize,
                                            brightness, contrast, saturation,
                                        )
                                    }.isSuccess
                                }
                                saving = false
                                Toast.makeText(
                                    context,
                                    if (ok) "Saved as copy" else "Save failed",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                if (ok) onSaved()
                            }
                        },
                        size       = 42.dp,
                        background = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    ) {
                        Icon(Icons.Rounded.Check, "Save copy", tint = Color.White)
                    }
                }
            }
        }

        // ── Bottom chrome: mode-morphing tool deck ──────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Tools morph between Crop and Adjust decks.
            AnimatedContent(
                targetState    = mode,
                transitionSpec = {
                    (slideInVertically(initialOffsetY = { it / 3 }, animationSpec = Motion.expressive()) +
                        fadeIn(Motion.standard()) + scaleIn(Motion.expressive(), initialScale = 0.92f)) togetherWith
                        (slideOutVertically(targetOffsetY = { it / 4 }, animationSpec = Motion.snappy()) +
                            fadeOut(Motion.snappy()))
                },
                label = "editor-deck",
            ) { m ->
                if (m == 0) {
                    // Crop deck: rotate + flip.
                    Row(
                        modifier = Modifier
                            .clip(ChromeShape)
                            .liquidGlass(alpha = 0.55f)
                            .glassSheen()
                            .liquidGlassBorder(ChromeShape)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(22.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        EditorTool("Rotate", Icons.Rounded.RotateRight) {
                            val before = snapshot()
                            rotationSteps += 1f
                            rememberChange(before)
                        }
                        EditorTool("Flip", Icons.Rounded.Flip) {
                            val before = snapshot()
                            flippedH = !flippedH
                            rememberChange(before)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text     = "Pinch & drag to crop",
                                fontSize = 11.sp,
                                color    = Color.White.copy(alpha = 0.55f),
                            )
                            Text(
                                text       = "${(zoom.value * 100).roundToInt()}%",
                                fontSize   = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color      = Color.White,
                            )
                        }
                    }
                } else {
                    // Adjust deck: three glass sliders.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ChromeShape)
                            .liquidGlass(alpha = 0.55f)
                            .glassSheen()
                            .liquidGlassBorder(ChromeShape)
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GlassSlider(
                            label = "Brightness",
                            value = brightness,
                            tint = Color(0xFFFFD479),
                            onInteractionStart = {
                                if (adjustmentGestureStart == null) adjustmentGestureStart = snapshot()
                            },
                            onInteractionEnd = {
                                adjustmentGestureStart?.let(::rememberChange)
                                adjustmentGestureStart = null
                            },
                            onChange = { brightness = it },
                        )
                        GlassSlider(
                            label = "Contrast",
                            value = contrast,
                            tint = Color(0xFF8FD3FF),
                            onInteractionStart = {
                                if (adjustmentGestureStart == null) adjustmentGestureStart = snapshot()
                            },
                            onInteractionEnd = {
                                adjustmentGestureStart?.let(::rememberChange)
                                adjustmentGestureStart = null
                            },
                            onChange = { contrast = it },
                        )
                        GlassSlider(
                            label = "Saturation",
                            value = saturation,
                            tint = Color(0xFFFF8FC2),
                            onInteractionStart = {
                                if (adjustmentGestureStart == null) adjustmentGestureStart = snapshot()
                            },
                            onInteractionEnd = {
                                adjustmentGestureStart?.let(::rememberChange)
                                adjustmentGestureStart = null
                            },
                            onChange = { saturation = it },
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            val originalInteraction = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .pressBounce(originalInteraction, pressedScale = 0.96f, spec = Motion.snappy(), haptic = false)
                    .clip(PillShape)
                    .liquidGlass(alpha = if (comparingOriginal) 0.64f else 0.38f)
                    .glassSheen()
                    .liquidGlassBorder(PillShape)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                comparingOriginal = true
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    comparingOriginal = false
                                }
                            }
                        )
                    }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = comparingOriginal,
                    transitionSpec = {
                        fadeIn(Motion.snappy()) togetherWith fadeOut(Motion.snappy())
                    },
                    label = "editor-original-compare",
                ) { showingOriginal ->
                    Text(
                        text = if (showingOriginal) "Original" else "Hold for original",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = if (showingOriginal) 1f else 0.72f),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LiquidTabBar(
                options       = listOf("Crop", "Adjust"),
                selectedIndex = mode,
                onSelect      = { mode = it },
                modifier      = Modifier.fillMaxWidth(0.6f),
            )
        }
    }
}

@Composable
private fun EditorTool(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BouncyIconButton(onClick = onClick, size = 48.dp) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, color = Color.White.copy(alpha = 0.70f))
    }
}

/** Draggable glass slider, -1..1 with a springy fill and value readout. */
@Composable
private fun GlassSlider(
    label: String,
    value: Float,
    tint: Color,
    onInteractionStart: () -> Unit = {},
    onInteractionEnd: () -> Unit = {},
    onChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f))
            Text(
                text     = "${if (value >= 0) "+" else ""}${(value * 100).roundToInt()}",
                fontSize = 12.sp,
                color    = tint,
            )
        }
        Spacer(Modifier.height(6.dp))
        var trackWidth by remember { mutableStateOf(0) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clip(PillShape)
                .background(Color.White.copy(alpha = 0.08f))
                .liquidGlassBorder(PillShape)
                .onSizeChanged { trackWidth = it.width }
                .pointerInput(trackWidth) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        onInteractionStart()
                        try {
                            if (trackWidth > 0) {
                                val t = (down.position.x / trackWidth).coerceIn(0f, 1f)
                                onChange(t * 2f - 1f)
                            }
                            var pressed = true
                            while (pressed) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                if (change != null && change.pressed && trackWidth > 0) {
                                    change.consume()
                                    val t = (change.position.x / trackWidth).coerceIn(0f, 1f)
                                    onChange(t * 2f - 1f)
                                }
                                pressed = event.changes.any { it.pressed }
                            }
                        } finally {
                            onInteractionEnd()
                        }
                    }
                },
        ) {
            val frac by animateFloatAsState(
                targetValue   = (value + 1f) / 2f,
                animationSpec = Motion.snappy(),
                label         = "slider-frac",
            )
            // Fill grows from the centre — negative values glow left, positive right.
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac.coerceIn(0.02f, 1f))
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(tint.copy(alpha = 0.10f), tint.copy(alpha = 0.45f))
                        )
                    )
            )
            // Thumb.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer {
                        translationX = frac * (trackWidth - 26.dp.toPx()).coerceAtLeast(0f)
                    }
                    .size(26.dp)
                    .clip(PillShape)
                    .background(Color.White.copy(alpha = 0.90f))
            )
        }
    }
}

// ── Bitmap plumbing (framework only) ────────────────────────────────────────

private fun loadScaledBitmap(context: Context, uri: Uri, maxDim: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opts)
    }
}

/** Android ColorMatrix for brightness/contrast/saturation, -1..1 each. */
private fun androidColorMatrix(brightness: Float, contrast: Float, saturation: Float): ColorMatrix {
    val cm = ColorMatrix()
    cm.setSaturation(1f + saturation)                 // 0 = grey, 2 = vivid
    val c = 1f + contrast * 0.8f
    val t = (1f - c) * 127.5f + brightness * 120f
    cm.postConcat(ColorMatrix(floatArrayOf(
        c, 0f, 0f, 0f, t,
        0f, c, 0f, 0f, t,
        0f, 0f, c, 0f, t,
        0f, 0f, 0f, 1f, 0f,
    )))
    return cm
}

/** Compose mirror of the same matrix so the live preview matches the save. */
private fun composeColorMatrix(
    brightness: Float, contrast: Float, saturation: Float,
): androidx.compose.ui.graphics.ColorMatrix {
    val android = androidColorMatrix(brightness, contrast, saturation)
    return androidx.compose.ui.graphics.ColorMatrix(android.array.copyOf())
}

/**
 * Renders rotation/flip → crop viewport → colour matrix onto a fresh Bitmap
 * and inserts it as a NEW image next to the original (IS_PENDING protocol).
 */
private fun saveEditedCopy(
    context: Context,
    src: Bitmap,
    rotationSteps: Int,
    flippedH: Boolean,
    zoom: Float,
    panX: Float,
    panY: Float,
    frame: IntSize,
    brightness: Float,
    contrast: Float,
    saturation: Float,
) {
    // 1. Rotate + flip into an oriented bitmap.
    val m = Matrix().apply {
        postRotate((rotationSteps % 4) * 90f)
        if (flippedH) postScale(-1f, 1f)
    }
    val oriented = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)

    // 2. Crop: reproduce the ContentScale.Fit viewport maths.
    val cropped = if (zoom > 1.01f && frame.width > 0 && frame.height > 0) {
        val fitScale   = min(frame.width.toFloat() / oriented.width, frame.height.toFloat() / oriented.height)
        val totalScale = fitScale * zoom
        val visibleW   = (frame.width / totalScale).coerceAtMost(oriented.width.toFloat())
        val visibleH   = (frame.height / totalScale).coerceAtMost(oriented.height.toFloat())
        val cx = oriented.width / 2f - panX / totalScale
        val cy = oriented.height / 2f - panY / totalScale
        val left = (cx - visibleW / 2f).coerceIn(0f, oriented.width - visibleW)
        val top  = (cy - visibleH / 2f).coerceIn(0f, oriented.height - visibleH)
        Bitmap.createBitmap(
            oriented,
            left.roundToInt(), top.roundToInt(),
            visibleW.roundToInt().coerceAtLeast(1), visibleH.roundToInt().coerceAtLeast(1),
        )
    } else oriented

    // 3. Colour pass onto the output canvas.
    val out = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
    Canvas(out).drawBitmap(
        cropped, 0f, 0f,
        Paint().apply { colorFilter = ColorMatrixColorFilter(androidColorMatrix(brightness, contrast, saturation)) },
    )

    // 4. Insert the copy via MediaStore.
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "GlassEdit_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val resolver = context.contentResolver
    val target = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("MediaStore insert failed")
    resolver.openOutputStream(target)?.use { out.compress(Bitmap.CompressFormat.JPEG, 94, it) }
        ?: error("openOutputStream failed")
    values.clear()
    values.put(MediaStore.Images.Media.IS_PENDING, 0)
    resolver.update(target, values, null, null)
}
