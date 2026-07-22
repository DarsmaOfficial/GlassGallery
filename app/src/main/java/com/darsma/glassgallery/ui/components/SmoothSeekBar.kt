package com.darsma.glassgallery.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * A smooth, expressive scrubber.
 *
 *  - Progress is spring-interpolated, so playback advances fluidly instead of
 *    snapping every 200 ms poll.
 *  - The track thickens and the thumb grows while the user is dragging.
 *  - Rounded caps, soft glow thumb, gradient-free but crisp.
 */
@Composable
fun SmoothSeekBar(
    progress: Float,
    isPlaying: Boolean = true,
    onScrubStart: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFFCFB8FF),
    inactiveColor: Color = Color.White.copy(alpha = 0.22f),
) {
    var dragging by remember { mutableStateOf(false) }
    var widthPx  by remember { mutableFloatStateOf(1f) }
    // While dragging we follow the finger directly; otherwise we follow playback.
    var dragProgress by remember { mutableFloatStateOf(progress) }

    val shownProgress = if (dragging) dragProgress else progress

    // Spring-smoothed value the canvas actually draws.
    val animatedProgress by animateFloatAsState(
        targetValue = shownProgress.coerceIn(0f, 1f),
        animationSpec = if (dragging) Motion.snappy() else Motion.standard(),
        label = "seek-progress",
    )

    val trackHeight by animateDpAsState(
        targetValue   = if (dragging) 8.dp else 5.dp,
        animationSpec = Motion.expressive(),
        label = "track-height",
    )
    val thumbRadius by animateDpAsState(
        targetValue   = if (dragging) 11.dp else 7.dp,
        animationSpec = Motion.bouncy(),
        label = "thumb-radius",
    )
    val glowAlpha by animateFloatAsState(
        targetValue   = if (dragging) 0.35f else 0f,
        animationSpec = Motion.standard(),
        label = "thumb-glow",
    )
    val activeAnimated by animateColorAsState(
        targetValue   = if (dragging) Color.White else activeColor,
        animationSpec = Motion.standard(),
        label = "active-color",
    )

    // ── Living wave ────────────────────────────────────────────────────────
    // While media plays, the *played* portion of the track is a travelling
    // sine wave; pausing or grabbing the thumb relaxes it back into a flat
    // line. Amplitude is spring-driven so the transition itself is a morph.
    val waveAmplitude by animateFloatAsState(
        targetValue   = if (!dragging && isPlaying) 1f else 0f,
        animationSpec = Motion.gentle(),
        label = "wave-amp",
    )
    val wavePhase by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue  = 0f,
        targetValue   = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 1100, easing = LinearEasing)
        ),
        label = "wave-phase",
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        dragging = true
                        onScrubStart()
                        dragProgress = (offset.x / widthPx).coerceIn(0f, 1f)
                        onScrub(dragProgress)
                        tryAwaitRelease()
                        dragging = false
                        onScrubEnd(dragProgress)
                    }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        dragging = true
                        onScrubStart()
                    },
                    onHorizontalDrag = { change, _ ->
                        dragProgress = (change.position.x / widthPx).coerceIn(0f, 1f)
                        onScrub(dragProgress)
                    },
                    onDragEnd = {
                        dragging = false
                        onScrubEnd(dragProgress)
                    },
                    onDragCancel = {
                        dragging = false
                        onScrubEnd(dragProgress)
                    },
                )
            },
    ) {
        widthPx = size.width
        val cy = size.height / 2f
        val th = trackHeight.toPx()
        val r  = thumbRadius.toPx()
        val usableW = size.width - r * 2f
        val thumbX  = r + usableW * animatedProgress

        // Inactive track
        drawRoundRect(
            color        = inactiveColor,
            topLeft      = Offset(0f, cy - th / 2f),
            size         = Size(size.width, th),
            cornerRadius = CornerRadius(th / 2f, th / 2f),
        )
        // Active track — flat capsule at rest, travelling wave while playing.
        val ampPx = 2.6.dp.toPx() * waveAmplitude
        if (ampPx > 0.4f) {
            val waveLen = 26.dp.toPx()
            val end     = thumbX.coerceAtLeast(th)
            val path    = Path()
            var x       = 0f
            val step    = waveLen / 14f
            while (x <= end) {
                // Flatten the wave as it approaches the thumb so the line
                // melts into it instead of clipping through.
                val fade = min(1f, (end - x) / waveLen)
                val y = cy + sin((x / waveLen) * 2f * PI.toFloat() - wavePhase) * ampPx * fade
                if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                x += step
            }
            path.lineTo(end, cy)
            drawPath(
                path  = path,
                color = activeAnimated,
                style = Stroke(width = th, cap = StrokeCap.Round),
            )
        } else {
            drawRoundRect(
                color        = activeAnimated,
                topLeft      = Offset(0f, cy - th / 2f),
                size         = Size(thumbX.coerceAtLeast(th), th),
                cornerRadius = CornerRadius(th / 2f, th / 2f),
            )
        }
        // Thumb glow
        if (glowAlpha > 0f) {
            drawCircle(
                color  = activeAnimated.copy(alpha = glowAlpha),
                radius = r * 2.2f,
                center = Offset(thumbX, cy),
            )
        }
        // Thumb
        drawCircle(
            color  = Color.White,
            radius = r,
            center = Offset(thumbX, cy),
            style  = Fill,
        )
        drawCircle(
            color  = activeAnimated,
            radius = r * 0.45f,
            center = Offset(thumbX, cy),
        )
    }
}
