package com.darsma.glassgallery.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darsma.glassgallery.ui.components.Motion

/** Which side of the screen a double-tap seek landed on. */
enum class SeekSide { NONE, FORWARD, BACKWARD }

/**
 * A YouTube-style double-tap seek flourish. Half the screen lights up with a
 * soft radial wash, an icon springs in, and the "+10s" label rises and fades.
 * Purely cosmetic feedback layered over the real seek call.
 */
@Composable
fun SeekRippleOverlay(
    side: SeekSide,
    seconds: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        SeekFlash(
            visible   = side == SeekSide.BACKWARD,
            forward   = false,
            seconds   = seconds,
            alignment = Alignment.CenterStart,
        )
        SeekFlash(
            visible   = side == SeekSide.FORWARD,
            forward   = true,
            seconds   = seconds,
            alignment = Alignment.CenterEnd,
        )
    }
}

@Composable
private fun BoxScope.SeekFlash(
    visible: Boolean,
    forward: Boolean,
    seconds: Int,
    alignment: Alignment,
) {
    // Drives the swell of the radial wash each time a burst fires.
    val swell by animateFloatAsState(
        targetValue   = if (visible) 1f else 0f,
        animationSpec = Motion.expressive(),
        label         = "seek-swell",
    )

    AnimatedVisibility(
        visible  = visible,
        enter    = fadeIn(Motion.snappy()),
        exit     = fadeOut(Motion.standard()),
        modifier = Modifier.align(alignment),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.42f)
                .graphicsLayer { alpha = 0.4f + 0.6f * swell }
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.20f),
                            Color.Transparent,
                        ),
                        radius = 520f,
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            val s = 0.6f + 0.4f * swell
                            scaleX = s
                            scaleY = s
                        }
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .height(58.dp)
                        .fillMaxWidth(0.34f),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = if (forward) Icons.Rounded.FastForward
                                             else Icons.Rounded.FastRewind,
                        contentDescription = null,
                        tint               = Color.White,
                        modifier           = Modifier.height(30.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text       = if (forward) "+$seconds s" else "-$seconds s",
                    color      = Color.White,
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.graphicsLayer {
                        alpha        = swell
                        translationY = (1f - swell) * 18f
                    },
                )
            }
        }
    }
}
