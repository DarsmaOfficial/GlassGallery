package com.darsma.glassgallery.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Identifies the visual role and prominence of a glass surface.
 */
enum class GlassRole {
    /** Low-emphasis glass embedded directly within content. */
    Inline,

    /** Persistent navigation or application chrome. */
    Chrome,

    /** Elevated controls layered above content. */
    Floating,

    /** High-emphasis, text-heavy overlay surfaces. */
    Modal,
}

/**
 * Defines the rendering values for one level of the glass hierarchy.
 *
 * @property tint base color applied to the glass surface
 * @property tintAlpha opacity of the surface tint
 * @property blurRadius backdrop blur radius
 * @property borderWidth width of the glass rim
 * @property rimLightAlpha opacity of the illuminated rim
 * @property rimDarkAlpha opacity of the shaded rim
 * @property highlightAlpha opacity of the surface highlight
 * @property shadowElevation elevation used for the surface shadow
 * @property defaultRadius default corner radius for the role
 */
@Immutable
data class GlassStyle(
    val tint: Color,
    val tintAlpha: Float,
    val blurRadius: Dp,
    val borderWidth: Dp,
    val rimLightAlpha: Float,
    val rimDarkAlpha: Float,
    val highlightAlpha: Float,
    val shadowElevation: Dp,
    val defaultRadius: Dp,
)

/**
 * Holds the complete glass-material ladder used throughout the app.
 *
 * @property inline low-emphasis glass embedded within content
 * @property chrome persistent navigation and application chrome
 * @property floating elevated controls layered above content
 * @property modal opaque, high-emphasis text-heavy surfaces
 */
@Immutable
data class GlassTokens(
    val inline: GlassStyle,
    val chrome: GlassStyle,
    val floating: GlassStyle,
    val modal: GlassStyle,
) {
    /**
     * Returns the style associated with [role].
     */
    fun styleFor(role: GlassRole): GlassStyle = when (role) {
        GlassRole.Inline -> inline
        GlassRole.Chrome -> chrome
        GlassRole.Floating -> floating
        GlassRole.Modal -> modal
    }
}

private val GlassTint = Color(0xFF17142A)

internal val DefaultGlassTokens = GlassTokens(
    inline = GlassStyle(
        tint = GlassTint,
        tintAlpha = 0.20f,
        blurRadius = 10.dp,
        borderWidth = 1.dp,
        rimLightAlpha = 0.14f,
        rimDarkAlpha = 0.05f,
        highlightAlpha = 0.06f,
        shadowElevation = 0.dp,
        defaultRadius = 12.dp,
    ),
    chrome = GlassStyle(
        tint = GlassTint,
        tintAlpha = 0.30f,
        blurRadius = 18.dp,
        borderWidth = 1.dp,
        rimLightAlpha = 0.22f,
        rimDarkAlpha = 0.07f,
        highlightAlpha = 0.09f,
        shadowElevation = 2.dp,
        defaultRadius = 22.dp,
    ),
    floating = GlassStyle(
        tint = GlassTint,
        tintAlpha = 0.42f,
        blurRadius = 24.dp,
        borderWidth = 1.dp,
        rimLightAlpha = 0.28f,
        rimDarkAlpha = 0.08f,
        highlightAlpha = 0.12f,
        shadowElevation = 10.dp,
        defaultRadius = 28.dp,
    ),
    modal = GlassStyle(
        tint = GlassTint,
        tintAlpha = 0.68f,
        blurRadius = 24.dp,
        borderWidth = 1.dp,
        rimLightAlpha = 0.18f,
        rimDarkAlpha = 0.06f,
        highlightAlpha = 0.06f,
        shadowElevation = 18.dp,
        defaultRadius = 30.dp,
    ),
)

/**
 * Composition-local access to the current glass-material token ladder.
 */
val LocalGlassTokens: ProvidableCompositionLocal<GlassTokens> =
    staticCompositionLocalOf { DefaultGlassTokens }

/**
 * Provides convenient composable access to Glass Gallery design tokens.
 */
object GlassTheme {
    /**
     * The glass-material token ladder active in the current composition.
     */
    val tokens: GlassTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalGlassTokens.current
}
