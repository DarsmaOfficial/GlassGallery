package com.darsma.glassgallery.ui.gallery

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.darsma.glassgallery.data.SortOrder
import com.darsma.glassgallery.ui.components.GlassSurface
import com.darsma.glassgallery.ui.components.Motion
import com.darsma.glassgallery.ui.components.pressBounce
import com.darsma.glassgallery.ui.components.specularFlash
import com.darsma.glassgallery.ui.theme.GlassRole
import com.darsma.glassgallery.ui.theme.GlassStyle
import com.darsma.glassgallery.ui.theme.GlassTheme
import com.darsma.glassgallery.ui.theme.PillShape

/**
 * A glass bottom-sheet for picking the gallery sort order. Animates in with a
 * scrim fade + spring slide; rows rise together as the sheet settles.
 */
@Composable
fun SortSheet(
    visible: Boolean,
    current: SortOrder,
    onSelect: (SortOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    // Scrim — fades the gallery behind the sheet.
    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(Motion.standard()),
        exit    = fadeOut(Motion.standard()),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = onDismiss,
                ),
        )
    }

    // The sheet itself.
    AnimatedVisibility(
        visible = visible,
        enter   = slideInVertically(
            initialOffsetY = { it },
            animationSpec  = Motion.expressive(),
        ) + fadeIn(Motion.standard()),
        exit    = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = Motion.standard(),
        ) + fadeOut(Motion.snappy()),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            val sheetShape = MaterialTheme.shapes.extraLarge
            GlassSurface(
                backdrop = null,
                role = GlassRole.Modal,
                shape = sheetShape,
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                Box(Modifier.matchParentSize().specularFlash(trigger = Unit))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                ) {
                    // Grabber handle.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(38.dp)
                            .height(4.dp)
                            .clip(PillShape)
                            .background(Color.White.copy(alpha = 0.22f))
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Sort videos",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
                    )

                    SortOrder.entries.forEach { order ->
                        SortRow(
                            order    = order,
                            selected = order == current,
                            onClick  = { onSelect(order) },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun SortRow(
    order: SortOrder,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // Entrance — rows rise together as the sheet settles into place, rather
    // than trickling in one after another.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val appear by animateFloatAsState(
        targetValue   = if (appeared) 1f else 0f,
        animationSpec = Motion.expressive(),
        label         = "sort-row-appear",
    )

    val interaction = remember { MutableInteractionSource() }
    val bg by animateFloatAsState(
        targetValue   = if (selected) 1f else 0f,
        animationSpec = Motion.standard(),
        label         = "sort-row-bg",
    )
    val inlineStyle = GlassTheme.tokens.styleFor(GlassRole.Inline)
    val rowShape = MaterialTheme.shapes.medium

    Row(
        modifier = Modifier
            .graphicsLayer {
                alpha        = appear
                translationY = (1f - appear) * 26f
            }
            .pressBounce(interaction, 0.97f, Motion.snappy(), haptic = false)
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(rowShape)
            .background(inlineStyle.tint.copy(alpha = inlineStyle.tintAlpha))
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f * bg)
            )
            .glassTokenBorder(rowShape, inlineStyle)
            .clickable(
                interactionSource = interaction,
                indication        = null,
                onClick           = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = order.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.78f),
            modifier = Modifier.weight(1f),
        )
        // Check mark pops in when selected.
        AnimatedVisibility(
            visible = selected,
            enter   = fadeIn(Motion.snappy()) + scaleIn(Motion.bouncy(), initialScale = 0.5f),
            exit    = fadeOut(Motion.snappy()) + scaleOut(Motion.snappy(), targetScale = 0.5f),
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Rounded.Check,
                    contentDescription = null,
                    tint               = Color.White,
                    modifier           = Modifier.size(15.dp),
                )
            }
        }
    }
}

private fun Modifier.glassTokenBorder(
    shape: Shape,
    style: GlassStyle,
): Modifier = border(
    width = style.borderWidth,
    brush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = style.rimLightAlpha),
            Color.White.copy(alpha = style.rimLightAlpha * 0.12f),
            Color.Black.copy(alpha = style.rimDarkAlpha * 0.12f),
            Color.Black.copy(alpha = style.rimDarkAlpha),
        ),
        start = Offset.Zero,
        end = Offset.Infinite,
    ),
    shape = shape,
)
