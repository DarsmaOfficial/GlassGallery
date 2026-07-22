package com.darsma.glassgallery.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private val BarShape = RoundedCornerShape(50)

/** Slow, restrained aurora that gives translucent chrome visual depth. */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val driftA by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aurora-a",
    )
    val driftB by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 26_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aurora-b",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0A0815), Color(0xFF05050B)),
                    )
                )
                val w = size.width
                val h = size.height
                val first = Offset(w * (0.06f + 0.30f * driftA), h * (0.02f + 0.09f * driftB))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF5E48A6).copy(alpha = 0.28f), Color.Transparent),
                        center = first,
                        radius = w * 0.88f,
                    ),
                    radius = w * 0.88f,
                    center = first,
                )
                val second = Offset(w * (0.98f - 0.30f * driftB), h * (0.34f + 0.11f * driftA))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF17618A).copy(alpha = 0.22f), Color.Transparent),
                        center = second,
                        radius = w * 0.78f,
                    ),
                    radius = w * 0.78f,
                    center = second,
                )
                val third = Offset(w * (0.40f + 0.15f * driftB), h * (0.96f - 0.08f * driftA))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF7B315D).copy(alpha = 0.16f), Color.Transparent),
                        center = third,
                        radius = w * 0.78f,
                    ),
                    radius = w * 0.78f,
                    center = third,
                )
            },
    )
}

/** Subtle moving highlight; one draw pass and no self-blur. */
@Composable
fun Modifier.liquidHighlight(): Modifier {
    val transition = rememberInfiniteTransition(label = "specular")
    val sweep by transition.animateFloat(
        initialValue = -0.7f,
        targetValue = 1.7f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 6000, delayMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "specular-x",
    )
    return this.drawWithContent {
        drawContent()
        val band = size.width * 0.30f
        val x = sweep * size.width
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.040f),
                    Color.Transparent,
                ),
                start = Offset(x, 0f),
                end = Offset(x + band, size.height),
            ),
        )
    }
}

/**
 * Compact floating navigation dock. Two centrally-defined springs animate its
 * leading and trailing edges independently, stretching the selected capsule
 * like a droplet before it settles.
 */
@Composable
fun LiquidTabBar(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector>? = null,
) {
    if (options.isEmpty()) return
    val primary = MaterialTheme.colorScheme.primary
    val accent = Color(0xFF9A7FEA)

    BoxWithConstraints(
        modifier = modifier
            .height(52.dp)
            .clip(BarShape)
            .liquidGlass(alpha = 0.70f)
            .opticalGlass(intensity = 0.78f, light = Offset(0.16f, 0.04f))
            .glassSheen()
            .liquidHighlight()
            .liquidGlassBorder(BarShape),
    ) {
        val haptic = LocalHapticFeedback.current
        val safeIndex = selectedIndex.coerceIn(options.indices)
        val segmentWidth = maxWidth / options.size
        val segmentPx = with(LocalDensity.current) { segmentWidth.toPx() }
        val density = LocalDensity.current

        // The two edges travel on different springs. During a tab change the
        // selected lens stretches across the gap like a strand of liquid, then
        // snaps back into a compact capsule with a tiny landing pulse.
        val leadingEdge = remember { Animatable(segmentPx * safeIndex) }
        val trailingEdge = remember { Animatable(segmentPx * safeIndex) }
        val settlePulse = remember { Animatable(0f) }
        LaunchedEffect(safeIndex, segmentPx) {
            val target = segmentPx * safeIndex
            settlePulse.snapTo(0f)
            launch { leadingEdge.animateTo(target, Motion.snappy()) }
            launch { trailingEdge.animateTo(target, Motion.expressive()) }
            delay(135L)
            settlePulse.animateTo(1f, tween(durationMillis = 90))
            settlePulse.animateTo(0f, Motion.bouncy())
        }

        val leftPx = min(leadingEdge.value, trailingEdge.value)
        val separation = abs(leadingEdge.value - trailingEdge.value)
        val widthPx = separation + segmentPx
        val tension = (separation / segmentPx.coerceAtLeast(1f)).coerceIn(0f, 1.6f)
        val tension01 = tension.coerceIn(0f, 1f)
        val movingRight = leadingEdge.value >= trailingEdge.value
        val indicatorShape = RoundedCornerShape(
            percent = (50f - tension01 * 14f).roundToInt().coerceIn(30, 50)
        )
        val indicatorScaleY = 1f - tension01 * 0.085f + settlePulse.value * 0.055f
        val indicatorScaleX = 1f + settlePulse.value * 0.025f

        Box(
            modifier = Modifier
                .offset { IntOffset(leftPx.roundToInt(), 0) }
                .width(with(density) { widthPx.toDp() })
                .fillMaxHeight()
                .padding(4.dp)
                .graphicsLayer {
                    scaleX = indicatorScaleX
                    scaleY = indicatorScaleY
                    transformOrigin = TransformOrigin.Center
                }
                .clip(indicatorShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            primary.copy(alpha = 0.40f + tension01 * 0.09f),
                            accent.copy(alpha = 0.30f + settlePulse.value * 0.08f),
                            primary.copy(alpha = 0.34f + tension01 * 0.05f),
                        )
                    )
                )
                .opticalGlass(
                    intensity = 0.64f + tension01 * 0.20f,
                    light = Offset(if (movingRight) 0.78f else 0.22f, 0.08f),
                )
                .liquidHighlight()
                .liquidGlassBorder(indicatorShape),
        ) {
            // A bright droplet rides the fast edge while the capsule is under
            // tension. It fades before landing, echoing the reference morph.
            if (tension > 0.025f) {
                Box(
                    modifier = Modifier
                        .align(if (movingRight) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(horizontal = 8.dp)
                        .size(6.dp + 3.dp * tension01)
                        .graphicsLayer {
                            alpha = (tension01 * 0.72f).coerceIn(0f, 1f)
                            scaleX = 0.75f + tension01 * 0.25f
                            scaleY = 1.15f - tension01 * 0.15f
                        }
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.78f)),
                )
            }

            if (settlePulse.value > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(7.dp + 11.dp * settlePulse.value)
                        .graphicsLayer { alpha = (1f - settlePulse.value) * 0.30f }
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.34f)),
                )
            }
        }

        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, label ->
                val selected = index == safeIndex
                val alpha by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.52f,
                    animationSpec = Motion.standard(),
                    label = "liquid-tab-alpha",
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.12f else 1f,
                    animationSpec = Motion.bouncy(),
                    label = "liquid-tab-icon",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (index != safeIndex) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(index)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (icons != null && index < icons.size) {
                            Icon(
                                imageVector = icons[index],
                                contentDescription = null,
                                tint = Color.White.copy(alpha = alpha),
                                modifier = Modifier
                                    .size(14.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                        translationY = if (selected) -1.5.dp.toPx() * (1f - settlePulse.value) else 0f
                                        if (selected) rotationZ = settlePulse.value * 5f
                                    },
                            )
                        }
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn(Motion.standard()) + scaleIn(Motion.expressive(), initialScale = 0.82f),
                            exit = fadeOut(Motion.snappy()) + scaleOut(Motion.snappy(), targetScale = 0.82f),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (icons != null) Spacer(Modifier.width(5.dp))
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    lineHeight = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = alpha),
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
