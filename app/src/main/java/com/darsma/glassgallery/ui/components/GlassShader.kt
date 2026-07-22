package com.darsma.glassgallery.ui.components

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.max

/**
 * Capability marker retained for call sites that may add optional AGSL later.
 * v20 deliberately avoids RenderEffect on glass chrome: a graphics-layer blur
 * blurs the element's own photo/video/text rather than the content behind it.
 */
object GlassShader {
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

/**
 * Safe, GPU-friendly specular sheen made from a single draw pass. It keeps text
 * and media razor sharp on every supported Android version.
 */
@Composable
fun Modifier.glassSheen(): Modifier {
    val transition = rememberInfiniteTransition(label = "glass-sheen")
    val travel by transition.animateFloat(
        initialValue = -0.8f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6200, delayMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glass-sheen-travel",
    )
    return this.drawWithContent {
        drawContent()
        val diagonal = max(size.width, size.height)
        val start = Offset(travel * size.width - diagonal * 0.25f, -diagonal * 0.2f)
        val end = Offset(start.x + diagonal * 0.48f, size.height + diagonal * 0.2f)
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.018f),
                    Color.White.copy(alpha = 0.070f),
                    Color.White.copy(alpha = 0.018f),
                    Color.Transparent,
                ),
                start = start,
                end = end,
            ),
        )
    }
}

/**
 * Compatibility no-op. A real backdrop blur needs explicit background capture;
 * applying RenderEffect here would self-blur the chrome and its content.
 */
@Composable
fun Modifier.frostedBlur(radius: Float = 24f): Modifier {
    @Suppress("UNUSED_VARIABLE") val ignoredRadius = radius
    return this
}

/**
 * Lightweight seek flourish: an expanding luminous ring drawn above content,
 * never a pixel-distortion RenderEffect. The player remains crisp and stable.
 */
@Composable
fun Modifier.seekRipple(
    centerX: Float,
    centerY: Float,
    progress: Float,
): Modifier {
    val p = progress.coerceIn(0f, 1f)
    if (p <= 0f || p >= 1f) return this
    return this.drawWithContent {
        drawContent()
        val center = Offset(
            x = centerX.coerceIn(0f, size.width),
            y = centerY.coerceIn(0f, size.height),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = (1f - p) * 0.10f),
                    Color.Transparent,
                ),
                center = center,
                radius = size.minDimension * (0.12f + p * 0.55f),
            ),
            center = center,
            radius = size.minDimension * (0.12f + p * 0.55f),
        )
    }
}
