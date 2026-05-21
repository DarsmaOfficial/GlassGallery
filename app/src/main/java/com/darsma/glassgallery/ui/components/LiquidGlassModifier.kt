package com.darsma.glassgallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Liquid-glass surface styling.
 *
 * NOTE: A real backdrop blur requires capturing the content *behind* the element.
 * Applying RenderEffect to graphicsLayer blurs the element's OWN content (including
 * text and video), which is wrong. So this implementation uses a translucent
 * gradient tint that reads as "frosted glass" without destroying content underneath.
 */
@Composable
fun Modifier.liquidGlass(
    tint: Color = Color(0xFF1A1730),
    alpha: Float = 0.55f,
): Modifier = this.background(
    brush = Brush.verticalGradient(
        colors = listOf(
            tint.copy(alpha = (alpha + 0.12f).coerceAtMost(1f)),
            tint.copy(alpha = alpha),
        )
    )
)

/**
 * A hairline gradient stroke that catches "light" along an edge the way real
 * glass does — brighter at the top-left, fading around the curve. Apply this
 * as a top overlay so it sits above images/scrims.
 */
fun Modifier.liquidGlassBorder(
    shape: Shape,
    width: Dp = 1.dp,
): Modifier = this.border(
    width = width,
    brush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.30f),
            Color.White.copy(alpha = 0.05f),
            Color.White.copy(alpha = 0.14f),
        )
    ),
    shape = shape,
)
