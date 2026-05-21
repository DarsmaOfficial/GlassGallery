package com.darsma.glassgallery.ui.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Liquid-glass surface styling.
 *
 * NOTE: A real backdrop blur requires capturing the content *behind* the element.
 * Applying RenderEffect to graphicsLayer blurs the element's OWN content (including
 * text and video), which is wrong. So this implementation uses a translucent
 * gradient tint that reads as "frosted glass" without destroying content underneath.
 *
 * @param tint base glass colour
 * @param alpha translucency of the glass
 */
@Composable
fun Modifier.liquidGlass(
    tint: Color = Color(0xFF1A1730),
    alpha: Float = 0.55f,
): Modifier = this.background(
    brush = Brush.verticalGradient(
        colors = listOf(
            tint.copy(alpha = alpha + 0.12f),
            tint.copy(alpha = alpha),
        )
    )
)
