package com.darsma.glassgallery.ui.components

import android.graphics.Matrix
import android.util.Log
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

private val pillPolygon: RoundedPolygon by lazy {
    RoundedPolygon(numVertices = 4, rounding = CornerRounding(radius = 1.0f))
}

private val roundedRectPolygon: RoundedPolygon by lazy {
    RoundedPolygon(numVertices = 4, rounding = CornerRounding(radius = 0.3f))
}

val playerMorph: Morph by lazy {
    try {
        Morph(start = pillPolygon, end = roundedRectPolygon)
    } catch (e: Exception) {
        Log.w("MorphShape", "Morph init failed", e)
        Morph(start = pillPolygon, end = pillPolygon) // safe fallback
    }
}

class MorphShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        return try {
            val matrix = Matrix().apply {
                setScale(size.width / 2f, size.height / 2f)
                postTranslate(size.width / 2f, size.height / 2f)
            }
            val androidPath = morph.toPath(progress = progress)
            androidPath.transform(matrix)
            val composePath = Path()
            composePath.asAndroidPath().addPath(androidPath)
            Outline.Generic(composePath)
        } catch (e: Exception) {
            Log.w("MorphShape", "createOutline failed", e)
            Outline.Rectangle(
                androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)
            )
        }
    }
}
