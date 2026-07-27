package com.darsma.glassgallery.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * A play/pause control whose *container* is alive. The background is a true
 * shape Morph (androidx.graphics.shapes): a perfect circle while paused that
 * blooms into an 8-petal scallop while playing, with a finite twist only while
 * the play/pause state is changing.
 */
@Composable
fun MorphingPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    size: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
    color: Color = Color.White.copy(alpha = 0.18f),
) {
    val morph = remember {
        val circle  = RoundedPolygon.circle(numVertices = 8).normalized()
        val scallop = RoundedPolygon.star(
            numVerticesPerRadius = 8,
            innerRadius          = 0.82f,
            rounding             = CornerRounding(radius = 0.30f),
        ).normalized()
        Morph(circle, scallop)
    }

    // Circle <-> scallop with a springy overshoot so the bloom feels organic.
    val morphProgress = animateFloatAsState(
        targetValue   = if (isPlaying) 1f else 0f,
        animationSpec = Motion.expressive(),
        label         = "pp-morph",
    )
    var spinTarget by remember { mutableFloatStateOf(0f) }
    val spin = animateFloatAsState(
        targetValue   = spinTarget,
        animationSpec = Motion.expressive(),
        label         = "pp-transition-spin",
    )
    var lastPlayingState by remember { mutableStateOf(isPlaying) }
    LaunchedEffect(isPlaying) {
        if (lastPlayingState == isPlaying) return@LaunchedEffect
        lastPlayingState = isPlaying
        spinTarget += if (isPlaying) 90f else -90f
    }

    val interaction = remember { MutableInteractionSource() }
    val androidPath = remember { android.graphics.Path() }
    val scaleMatrix = remember { android.graphics.Matrix() }

    Box(
        modifier = modifier
            .pressBounce(interaction, pressedScale = 0.82f, spec = Motion.snappy())
            .size(size)
            .drawBehind {
                morph.toPath(morphProgress.value.coerceIn(0f, 1f), androidPath)
                scaleMatrix.reset()
                scaleMatrix.setScale(this.size.width, this.size.height)
                androidPath.transform(scaleMatrix)
                rotate(degrees = spin.value) {
                    drawPath(path = androidPath.asComposePath(), color = color)
                }
            }
            .clickable(
                interactionSource = interaction,
                indication        = null,
                onClick           = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState    = isPlaying,
            transitionSpec = {
                scaleIn(Motion.bouncy(), initialScale = 0.6f) + fadeIn(Motion.snappy()) togetherWith
                    scaleOut(Motion.snappy(), targetScale = 0.6f) + fadeOut(Motion.snappy())
            },
            label = "pp-icon",
        ) { playing ->
            Icon(
                imageVector        = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint               = Color.White,
                modifier           = Modifier.size(iconSize),
            )
        }
    }
}

/**
 * A celebratory shockwave: every time [isOn] flips to true, a soft ring
 * erupts outward from behind the element and dissolves. Skips the very first
 * composition so already-favourited items don't burst as they scroll in.
 */
@Composable
fun Modifier.favoriteBurst(
    isOn: Boolean,
    color: Color = Color(0xFFFF5C8A),
): Modifier {
    val progress = remember { Animatable(1f) }
    var firstEmission by remember { mutableStateOf(true) }
    LaunchedEffect(isOn) {
        if (firstEmission) {
            firstEmission = false
            return@LaunchedEffect
        }
        if (isOn) {
            progress.snapTo(0f)
            progress.animateTo(
                targetValue   = 1f,
                animationSpec = tween(durationMillis = 560, easing = FastOutSlowInEasing),
            )
        }
    }
    return this.drawBehind {
        val p = progress.value
        if (p < 1f) {
            val eased = FastOutSlowInEasing.transform(p.coerceIn(0f, 1f))
            val fade = (1f - p) * (1f - p)
            val center = Offset(size.width / 2f, size.height / 2f)

            // Elastic shockwave, kept inside the generous heart touch target.
            drawCircle(
                color = color.copy(alpha = fade * 0.60f),
                radius = size.minDimension * (0.25f + 0.21f * eased),
                center = center,
                style = Stroke(width = 2.6.dp.toPx() * (1f - p) + 0.6.dp.toPx()),
            )

            // Eight tiny droplets fly out with alternating pink/white glints.
            repeat(8) { index ->
                val angle = (index * 45.0 - 90.0) * PI / 180.0
                val distance = size.minDimension * (0.16f + 0.24f * eased)
                val trailStartDistance = distance - size.minDimension * (0.07f * (1f - p))
                val end = Offset(
                    x = center.x + cos(angle).toFloat() * distance,
                    y = center.y + sin(angle).toFloat() * distance,
                )
                val start = Offset(
                    x = center.x + cos(angle).toFloat() * trailStartDistance,
                    y = center.y + sin(angle).toFloat() * trailStartDistance,
                )
                val particleColor = if (index % 2 == 0) color else Color.White
                drawLine(
                    color = particleColor.copy(alpha = fade * 0.42f),
                    start = start,
                    end = end,
                    strokeWidth = 1.1.dp.toPx() * (1f - p) + 0.25.dp.toPx(),
                )
                drawCircle(
                    color = particleColor.copy(alpha = fade * 0.92f),
                    radius = 1.9.dp.toPx() * (1f - p) + 0.35.dp.toPx(),
                    center = end,
                )
            }
        }
    }
}
