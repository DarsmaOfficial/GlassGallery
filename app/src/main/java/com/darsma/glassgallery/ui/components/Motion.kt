package com.darsma.glassgallery.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
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

    /** Large surfaces travelling through depth. */
    fun <T> spatial(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.80f, stiffness = 235f)

    /** Connected glass shapes stretching under surface tension. */
    fun <T> morph(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.62f, stiffness = 340f)

    /** Fast elastic response for gesture release. */
    fun <T> elastic(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.56f, stiffness = 470f)

    /** Critically damped final alignment. */
    fun <T> settle(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 1f, stiffness = 260f)

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
    haptic: Boolean = true,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    if (haptic) interactionSource.hapticPress()
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
    val pressed by interaction.collectIsPressedAsState()
    // Circle <-> rounded-square morph: the button's silhouette squares off
    // slightly under the finger, then relaxes back to a circle.
    val cornerPct by animateFloatAsState(
        targetValue   = if (pressed) 32f else 50f,
        animationSpec = Motion.standard(),
        label         = "icon-btn-corner",
    )
    Box(
        modifier = modifier
            .pressBounce(interaction, pressedScale, Motion.snappy())
            .size(size)
            .clip(RoundedCornerShape(percent = cornerPct.toInt().coerceIn(0, 50)))
            .background(background)
            .clickable(
                interactionSource = interaction,
                indication        = null,
                onClick           = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Lightweight shimmer sweep for loading placeholders. A soft diagonal band of
 * light travels across the surface — the universal "content is on its way"
 * signal, drawn entirely in one draw call.
 */
@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val travel by transition.animateFloat(
        initialValue  = -1f,
        targetValue   = 2f,
        animationSpec = infiniteRepeatable(
            animation  = tween(durationMillis = 1300, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-travel",
    )
    return this.drawWithContent {
        drawContent()
        val band = size.width * 0.55f
        val x    = travel * size.width
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.07f),
                    Color.Transparent,
                ),
                start = Offset(x, 0f),
                end   = Offset(x + band, size.height),
            ),
        )
    }
}
