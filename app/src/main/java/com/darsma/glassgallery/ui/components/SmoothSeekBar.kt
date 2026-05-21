package com.darsma.glassgallery.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas

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
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = if (dragging) Spring.StiffnessHigh else Spring.StiffnessLow,
        ),
        label = "seek-progress",
    )

    val trackHeight by animateDpAsState(
        targetValue   = if (dragging) 8.dp else 5.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "track-height",
    )
    val thumbRadius by animateDpAsState(
        targetValue   = if (dragging) 11.dp else 7.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "thumb-radius",
    )
    val glowAlpha by animateFloatAsState(
        targetValue   = if (dragging) 0.35f else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "thumb-glow",
    )
    val activeAnimated by animateColorAsState(
        targetValue   = if (dragging) Color.White else activeColor,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "active-color",
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
        // Active track
        drawRoundRect(
            color        = activeAnimated,
            topLeft      = Offset(0f, cy - th / 2f),
            size         = Size(thumbX.coerceAtLeast(th), th),
            cornerRadius = CornerRadius(th / 2f, th / 2f),
        )
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
