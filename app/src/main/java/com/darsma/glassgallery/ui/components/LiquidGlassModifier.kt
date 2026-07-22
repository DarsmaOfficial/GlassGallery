package com.darsma.glassgallery.ui.components

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Layered translucent tint used by every v20 chrome surface. It mimics depth
 * with soft gradients while keeping everything underneath and inside sharp.
 */
@Composable
fun Modifier.liquidGlass(
    tint: Color = Color(0xFF17142A),
    alpha: Float = 0.55f,
): Modifier = this.drawBehind {
    val safeAlpha = alpha.coerceIn(0f, 1f)
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                tint.copy(alpha = (safeAlpha + 0.13f).coerceAtMost(0.92f)),
                tint.copy(alpha = safeAlpha),
                Color(0xFF090812).copy(alpha = (safeAlpha * 0.78f).coerceAtMost(0.82f)),
            ),
        ),
    )
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.075f * safeAlpha),
                Color.Transparent,
            ),
            center = Offset(size.width * 0.16f, 0f),
            radius = size.maxDimension * 0.92f,
        ),
    )
}

/**
 * Subtle optical rim: bright at the top-left, almost invisible elsewhere.
 */
fun Modifier.liquidGlassBorder(
    shape: Shape,
    width: Dp = 1.dp,
): Modifier = this.border(
    width = width,
    brush = Brush.linearGradient(
        colorStops = arrayOf(
            0.00f to Color.White.copy(alpha = 0.30f),
            0.32f to Color.White.copy(alpha = 0.12f),
            0.70f to Color.White.copy(alpha = 0.035f),
            1.00f to Color.White.copy(alpha = 0.15f),
        ),
        start = Offset.Zero,
        end = Offset.Infinite,
    ),
    shape = shape,
)
