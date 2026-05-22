package com.darsma.glassgallery.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/**
 * Central motion vocabulary. Every animated surface in the app pulls its spring
 * from here so timing feels consistent — the hallmark of a polished product.
 */
object Motion {

    /** Quick, crisp response for taps and small state flips. Settles fast, no wobble. */
    fun <T> snappy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessHigh)

    /** The standard UI spring — smooth with a barely-there settle. */
    fun <T> standard(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.85f, stiffness = 380f)

    /** Expressive spring with a gentle overshoot — for hero/morph transitions. */
    fun <T> expressive(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.72f, stiffness = 300f)

    /** Playful pop with visible overshoot — for the favorite heart, badges, etc. */
    fun <T> bouncy(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.42f, stiffness = 520f)

    /** Slow, soft fade for ambient/secondary content. */
    fun <T> gentle(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = Spring.StiffnessVeryLow)
}

/**
 * Modifier that gives any composable a high-quality press response: a smooth
 * scale-down on touch that springs back on release. Press feedback is the
 * single biggest contributor to an interface feeling "alive".
 */
@Composable
fun Modifier.pressBounce(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.90f,
    spec: AnimationSpec<Float> = Motion.standard(),
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue   = if (pressed) pressedScale else 1f,
        animationSpec = spec,
        label         = "press-bounce",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        transformOrigin = TransformOrigin.Center
    }
}

/**
 * A circular icon button with a consistent, professional press animation and
 * an optional tint backdrop. Used across the player and gallery chrome.
 */
@Composable
fun BouncyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp,
    background: Color = Color.White.copy(alpha = 0.10f),
    pressedScale: Float = 0.84f,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressBounce(interaction, pressedScale, Motion.snappy())
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication        = null,
                onClick           = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}
