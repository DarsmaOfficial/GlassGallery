package com.darsma.glassgallery.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val HeroStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 28.sp,
    lineHeight = 32.sp,
    letterSpacing = (-0.4).sp,
)

private val LargeTitleStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Bold,
    fontSize = 22.sp,
    lineHeight = 27.sp,
    letterSpacing = (-0.2).sp,
)

private val SurfaceTitleStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 21.sp,
    letterSpacing = 0.sp,
)

private val BodyStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp,
)

private val SupportingBodyStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 17.sp,
    letterSpacing = 0.1.sp,
)

private val ActionLabelStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.2.sp,
)

private val MetadataStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.2.sp,
)

private val MicroMetadataStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.sp,
    lineHeight = 12.sp,
    letterSpacing = 0.3.sp,
)

/**
 * Glass Gallery's semantic typography scale, mapped onto Material 3 slots.
 *
 * `displaySmall` and `headlineMedium` share the 28sp hero/headline style;
 * `titleLarge` is the 22sp large title; `titleMedium` is the 16sp surface title;
 * body and label slots descend through body, supporting text, actions, and metadata.
 */
val GlassTypography: Typography = Typography(
    displaySmall = HeroStyle,
    headlineMedium = HeroStyle,
    titleLarge = LargeTitleStyle,
    titleMedium = SurfaceTitleStyle,
    bodyLarge = BodyStyle,
    bodyMedium = SupportingBodyStyle,
    labelLarge = ActionLabelStyle,
    labelMedium = MetadataStyle,
    labelSmall = MicroMetadataStyle,
)

/**
 * A base text style with tabular figures for stable-width durations and counts.
 */
val TabularFigures: TextStyle = TextStyle(
    fontFamily = FontFamily.Default,
    fontFeatureSettings = "tnum",
)
