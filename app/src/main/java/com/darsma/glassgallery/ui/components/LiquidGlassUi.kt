package com.darsma.glassgallery.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private val BarShape = RoundedCornerShape(50)

/**
 * Ambient liquid backdrop: two soft aurora blobs of colour drift slowly
 * behind the whole gallery, so the glass chrome always has something living
 * to refract. Pure draw calls — costs almost nothing.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val driftA by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 16_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aurora-a",
    )
    val driftB by transition.animateFloat(
        initialValue  = 0f,
        targetValue   = 1f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 23_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "aurora-b",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(Color(0xFF070710))
                val w = size.width
                val h = size.height
                val c1 = Offset(w * (0.12f + 0.28f * driftA), h * (0.05f + 0.10f * driftB))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF2C1C62).copy(alpha = 0.55f), Color.Transparent),
                        center = c1,
                        radius = w * 0.95f,
                    ),
                    radius = w * 0.95f,
                    center = c1,
                )
                val c2 = Offset(w * (0.95f - 0.25f * driftB), h * (0.34f + 0.14f * driftA))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF123250).copy(alpha = 0.42f), Color.Transparent),
                        center = c2,
                        radius = w * 0.85f,
                    ),
                    radius = w * 0.85f,
                    center = c2,
                )
                val c3 = Offset(w * (0.45f + 0.18f * driftA), h * (0.92f - 0.06f * driftB))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF3A1140).copy(alpha = 0.30f), Color.Transparent),
                        center = c3,
                        radius = w * 0.80f,
                    ),
                    radius = w * 0.80f,
                    center = c3,
                )
            },
    )
}

/**
 * A slow specular streak that sweeps across a glass surface every few
 * seconds — the "light catching the glass" signature of liquid glass.
 * Extremely subtle by design.
 */
@Composable
fun Modifier.liquidHighlight(): Modifier {
    val transition = rememberInfiniteTransition(label = "specular")
    val sweep by transition.animateFloat(
        initialValue  = -0.7f,
        targetValue   = 1.7f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 5400, delayMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "specular-x",
    )
    return this.drawWithContent {
        drawContent()
        val band = size.width * 0.38f
        val x    = sweep * size.width
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.055f),
                    Color.Transparent,
                ),
                start = Offset(x, 0f),
                end   = Offset(x + band, size.height),
            ),
        )
    }
}

/**
 * Floating liquid-glass tab bar. The selection indicator is driven by TWO
 * springs — a fast edge and a lazy edge — so while it travels between
 * segments it physically stretches like a droplet, then contracts and
 * settles. This is the morph that makes switching feel liquid.
 */
@Composable
fun LiquidTabBar(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector>? = null,
) {
    BoxWithConstraints(
        modifier = modifier
            .height(54.dp)
            .clip(BarShape)
            .liquidGlass(alpha = 0.60f)
            .glassSheen()
            .liquidHighlight()
            .liquidGlassBorder(BarShape),
    ) {
        val haptic   = LocalHapticFeedback.current
        val segWidth = maxWidth / options.size
        val segPx    = with(LocalDensity.current) { segWidth.toPx() }
        val density  = LocalDensity.current

        // Two edges, two temperaments: the fast edge races ahead, the lazy
        // edge trails behind — min/max between them is the stretching droplet.
        val fastEdge = remember { Animatable(segPx * selectedIndex) }
        val lazyEdge = remember { Animatable(segPx * selectedIndex) }
        LaunchedEffect(selectedIndex, segPx) {
            val target = segPx * selectedIndex
            launch { fastEdge.animateTo(target, spring(dampingRatio = 0.78f, stiffness = 620f)) }
            launch { lazyEdge.animateTo(target, spring(dampingRatio = 0.88f, stiffness = 150f)) }
        }
        val leftPx  = min(fastEdge.value, lazyEdge.value)
        val widthPx = abs(fastEdge.value - lazyEdge.value) + segPx

        Box(
            modifier = Modifier
                .offset { IntOffset(leftPx.roundToInt(), 0) }
                .width(with(density) { widthPx.toDp() })
                .fillMaxHeight()
                .padding(5.dp)
                .clip(BarShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.30f))
                .liquidGlassBorder(BarShape),
        )

        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val textAlpha by animateFloatAsState(
                    targetValue   = if (selected) 1f else 0.55f,
                    animationSpec = Motion.standard(),
                    label         = "liquid-tab-alpha",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication        = null,
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(index)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (icons != null) {
                            // Icons breathe: they pop up in scale as their tab
                            // becomes selected, then relax.
                            val iconScale by animateFloatAsState(
                                targetValue   = if (selected) 1.18f else 1f,
                                animationSpec = Motion.bouncy(),
                                label         = "liquid-tab-icon",
                            )
                            Icon(
                                imageVector        = icons[index],
                                contentDescription = null,
                                tint               = Color.White.copy(alpha = textAlpha),
                                modifier           = Modifier
                                    .size(15.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                            )
                            Spacer(Modifier.width(5.dp))
                        }
                        Text(
                            text       = label,
                            fontSize   = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color      = Color.White.copy(alpha = textAlpha),
                        )
                    }
                }
            }
        }
    }
}
