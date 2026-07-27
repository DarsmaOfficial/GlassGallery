package com.darsma.glassgallery.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Glass Gallery's five-step corner-radius scale for Material 3 surfaces.
 */
val GlassShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * A fully rounded shape for pills, chips, and circular controls.
 */
val PillShape: RoundedCornerShape = RoundedCornerShape(percent = 50)
