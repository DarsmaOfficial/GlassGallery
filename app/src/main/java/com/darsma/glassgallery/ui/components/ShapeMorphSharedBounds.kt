package com.darsma.glassgallery.ui.components

import android.graphics.Matrix
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

private val pillPolygon: RoundedPolygon = RoundedPolygon.rectangle(
    width   = 4f,
    height  = 1f,
    rounding = CornerRounding(radius = 1.0f),
)

private val roundedRectPolygon: RoundedPolygon = RoundedPolygon.rectangle(
    width    = 3f,
    height   = 2f,
    rounding = CornerRounding(radius = 0.28f),
)

val playerMorph: Morph = Morph(start = pillPolygon, end = roundedRectPolygon)

class MorphShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val matrix = Matrix().apply {
            setScale(size.width / 2f, size.height / 2f)
            postTranslate(size.width / 2f, size.height / 2f)
        }
        val androidPath = morph.toPath(progress = progress)
        androidPath.transform(matrix)
        val composePath = Path()
        composePath.asAndroidPath().addPath(androidPath)
        return Outline.Generic(composePath)
    }
}
