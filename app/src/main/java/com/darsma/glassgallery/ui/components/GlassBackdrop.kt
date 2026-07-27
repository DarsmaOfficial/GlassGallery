package com.darsma.glassgallery.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.darsma.glassgallery.ui.theme.GlassRole
import com.darsma.glassgallery.ui.theme.GlassTheme
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shared capture state for one [GlassBackdropHost].
 *
 * The source [GraphicsLayer] is intentionally internal and must remain effect-free. A
 * [GraphicsLayer] records a RenderNode/display-list graph, not a copied bitmap. If a blur is set on
 * this source, every consumer inherits the blur and cannot sample the original pixels. More
 * importantly, recording glass chrome into the source would make a consumer refer to a layer that
 * eventually refers back to that same consumer. That recursive RenderNode graph can produce blank
 * or stale frames, and may fail rendering entirely.
 *
 * Use one state with one host at a time. This engine is deliberately intended for at most two or
 * three visible chrome surfaces. Each consumer replays the source display list into a small local
 * effect layer, so it must not be used to blur individual media or album cards.
 */
@Stable
class GlassBackdropState internal constructor(
    internal val sourceLayer: GraphicsLayer,
) {
    internal var hostCoordinates: LayoutCoordinates? by mutableStateOf(null)
        private set

    internal var captureAvailable: Boolean by mutableStateOf(false)
        private set

    internal fun updateHostCoordinates(coordinates: LayoutCoordinates) {
        if (hostCoordinates !== coordinates) {
            hostCoordinates = coordinates
            captureAvailable = false
        }
    }

    internal fun updateCaptureAvailability(available: Boolean) {
        if (captureAvailable != available) {
            captureAvailable = available
        }
    }
}

/**
 * Remembers a source layer and its host-coordinate state.
 *
 * The returned state owns no bitmap and performs no readback. It is released with the composition
 * by [rememberGraphicsLayer].
 */
@Composable
fun rememberGlassBackdropState(): GlassBackdropState {
    val sourceLayer = rememberGraphicsLayer()
    return remember(sourceLayer) { GlassBackdropState(sourceLayer) }
}

/**
 * Separates capturable background content from chrome that consumes the capture.
 *
 * [source] is emitted first and is the only content recorded into the shared source layer. It is
 * then drawn normally, without a RenderEffect, so the actual background stays sharp. [overlay] is
 * emitted second and is never part of that recording. This structural ordering is required; a
 * z-index cannot make a recursively recorded layer safe.
 *
 * The host is normally used with a bounded modifier such as `fillMaxSize()`. Both slots match those
 * host bounds and share its top-left coordinate origin.
 */
@Composable
fun GlassBackdropHost(
    state: GlassBackdropState,
    modifier: Modifier = Modifier,
    source: @Composable BoxScope.() -> Unit,
    overlay: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.onGloballyPositioned(state::updateHostCoordinates),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .recordBackdropSource(state),
            content = source,
        )
        Box(
            modifier = Modifier.matchParentSize(),
            content = overlay,
        )
    }
}

/**
 * Draws a shape-clipped glass surface whose backdrop remains independent of its sharp content.
 *
 * A successful hardware path records an expanded region of [GlassBackdropState.sourceLayer] into
 * this surface's own local layer. The expansion is twice the blur radius on every side. RenderEffect
 * forces an offscreen buffer clipped to layer bounds, so recording only the visible surface bounds
 * would make the blur sample transparent pixels at every edge and create a dark halo.
 *
 * The shared source layer is only replayed here; its `renderEffect` is never assigned. The local
 * layer uses [TileMode.Clamp], then tint, highlight, sharp [content], and rim are drawn outside that
 * effect layer. Missing capture, previews, the first frame, software canvases, invalid coordinates,
 * and any capture failure all use a non-transparent tinted-gradient fallback.
 */
@Composable
fun GlassSurface(
    backdrop: GlassBackdropState?,
    role: GlassRole,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val style = GlassTheme.tokens.styleFor(role)
    val effectLayer = rememberGraphicsLayer()
    val inspectionMode = LocalInspectionMode.current
    var surfaceCoordinates: LayoutCoordinates? by remember(backdrop) { mutableStateOf(null) }
    var firstDrawComplete by remember(backdrop) { mutableStateOf(false) }

    val rimBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = style.rimLightAlpha.coerceIn(0f, 1f)),
            Color.White.copy(
                alpha = style.rimLightAlpha.coerceIn(0f, 1f) * 0.12f,
            ),
            Color.Black.copy(
                alpha = style.rimDarkAlpha.coerceIn(0f, 1f) * 0.12f,
            ),
            Color.Black.copy(alpha = style.rimDarkAlpha.coerceIn(0f, 1f)),
        ),
        start = Offset.Zero,
        end = Offset.Infinite,
    )

    val shadowModifier = if (style.shadowElevation.value > 0f) {
        Modifier.shadow(
            elevation = style.shadowElevation,
            shape = shape,
            clip = false,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(shadowModifier)
            .onGloballyPositioned { coordinates ->
                if (surfaceCoordinates !== coordinates) {
                    surfaceCoordinates = coordinates
                }
            }
            .clip(shape)
            .drawWithCache {
                val blurRadiusPx = style.blurRadius.toPx()
                val validBlurRadius = blurRadiusPx.isFinite() && blurRadiusPx >= 0f
                val paddingPx = if (validBlurRadius) {
                    ceil(blurRadiusPx * EFFECT_PADDING_MULTIPLIER).toInt().coerceAtLeast(0)
                } else {
                    0
                }

                val widthPx = if (size.width.isFinite()) {
                    size.width.roundToInt().coerceAtLeast(0)
                } else {
                    0
                }
                val heightPx = if (size.height.isFinite()) {
                    size.height.roundToInt().coerceAtLeast(0)
                } else {
                    0
                }
                val expandedWidth = widthPx.toLong() + paddingPx.toLong() * 2L
                val expandedHeight = heightPx.toLong() + paddingPx.toLong() * 2L
                val validExpandedSize = expandedWidth in 1L..Int.MAX_VALUE.toLong() &&
                    expandedHeight in 1L..Int.MAX_VALUE.toLong()
                val expandedSize = if (validExpandedSize) {
                    IntSize(expandedWidth.toInt(), expandedHeight.toInt())
                } else {
                    IntSize.Zero
                }

                val blurEffect = if (
                    !inspectionMode &&
                    validBlurRadius &&
                    blurRadiusPx > 0f
                ) {
                    runCatching {
                        BlurEffect(
                            radiusX = blurRadiusPx,
                            radiusY = blurRadiusPx,
                            edgeTreatment = TileMode.Clamp,
                        )
                    }.getOrNull()
                } else {
                    null
                }
                val blurEffectReady = blurRadiusPx == 0f || blurEffect != null

                val fallbackAlpha = max(
                    MINIMUM_FALLBACK_ALPHA,
                    style.tintAlpha.coerceIn(0f, 1f),
                )
                val fallbackBrush = Brush.linearGradient(
                    colors = listOf(
                        style.tint.copy(alpha = (fallbackAlpha + 0.10f).coerceAtMost(1f)),
                        Color.White.copy(alpha = 0.08f),
                        style.tint.copy(alpha = fallbackAlpha),
                    ),
                    start = Offset.Zero,
                    end = Offset(
                        x = size.width.coerceAtLeast(1f),
                        y = size.height.coerceAtLeast(1f),
                    ),
                )
                val highlightBrush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = style.highlightAlpha.coerceIn(0f, 1f)),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.14f, size.height * 0.08f),
                    radius = max(size.width, size.height).coerceAtLeast(1f) * 0.90f,
                )

                onDrawWithContent {
                    val hostCoordinates = backdrop?.hostCoordinates
                    val localCoordinates = surfaceCoordinates
                    val sourceLayer = backdrop?.sourceLayer
                    val sourceOffset = if (
                        hostCoordinates != null &&
                        localCoordinates != null &&
                        hostCoordinates.isAttached &&
                        localCoordinates.isAttached
                    ) {
                        runCatching {
                            hostCoordinates.localPositionOf(localCoordinates, Offset.Zero)
                        }.getOrNull()
                    } else {
                        null
                    }

                    val coordinatesValid = sourceOffset != null &&
                        sourceOffset.x.isFinite() &&
                        sourceOffset.y.isFinite()
                    val sourceReady = backdrop?.captureAvailable == true &&
                        sourceLayer != null &&
                        sourceLayer.size != IntSize.Zero

                    val hardwareCanvas = if (
                        firstDrawComplete &&
                        !inspectionMode &&
                        widthPx > 0 &&
                        heightPx > 0 &&
                        validExpandedSize &&
                        validBlurRadius &&
                        blurEffectReady &&
                        coordinatesValid &&
                        sourceReady
                    ) {
                        runCatching {
                            var accelerated = false
                            drawIntoCanvas { canvas ->
                                accelerated = canvas.nativeCanvas.isHardwareAccelerated
                            }
                            accelerated
                        }.getOrDefault(false)
                    } else {
                        false
                    }

                    val sampledBackdropDrawn = if (
                        hardwareCanvas &&
                        sourceLayer != null &&
                        sourceOffset != null
                    ) {
                        runCatching {
                            effectLayer.topLeft = IntOffset(-paddingPx, -paddingPx)
                            effectLayer.renderEffect = blurEffect
                            effectLayer.record(size = expandedSize) {
                                // Padding can extend beyond the host at a screen edge. A tinted
                                // underlay prevents those pixels from becoming transparent blur
                                // input while the translated, normally opaque source stays exact.
                                drawRect(
                                    color = style.tint.copy(alpha = MINIMUM_FALLBACK_ALPHA),
                                )
                                translate(
                                    left = paddingPx.toFloat() - sourceOffset.x,
                                    top = paddingPx.toFloat() - sourceOffset.y,
                                ) {
                                    drawLayer(sourceLayer)
                                }
                            }
                            drawLayer(effectLayer)
                        }.isSuccess
                    } else {
                        false
                    }

                    if (!sampledBackdropDrawn) {
                        drawRect(brush = fallbackBrush)
                    }

                    drawRect(
                        color = style.tint,
                        alpha = style.tintAlpha.coerceIn(0f, 1f),
                    )
                    drawRect(brush = highlightBrush)

                    drawContent()

                    if (!firstDrawComplete) {
                        firstDrawComplete = true
                    }
                }
            }
            .border(
                width = style.borderWidth,
                brush = rimBrush,
                shape = shape,
            ),
        content = content,
    )
}

/**
 * Records only the host's source slot, then draws that same effect-free layer normally.
 *
 * Do not assign `state.sourceLayer.renderEffect` here. Also do not move this modifier to a parent
 * containing the overlay: either change would violate the acyclic source-to-consumer graph.
 */
private fun Modifier.recordBackdropSource(state: GlassBackdropState): Modifier = drawWithCache {
    onDrawWithContent {
        val drawableSize = size.width.isFinite() &&
            size.height.isFinite() &&
            size.width > 0f &&
            size.height > 0f

        val capturedAndDrawn = if (drawableSize) {
            runCatching {
                state.sourceLayer.topLeft = IntOffset.Zero
                state.sourceLayer.record {
                    this@onDrawWithContent.drawContent()
                }
                drawLayer(state.sourceLayer)
            }.isSuccess
        } else {
            false
        }

        state.updateCaptureAvailability(capturedAndDrawn)
        if (!capturedAndDrawn) {
            drawContent()
        }
    }
}

private const val EFFECT_PADDING_MULTIPLIER = 2f
private const val MINIMUM_FALLBACK_ALPHA = 0.22f
