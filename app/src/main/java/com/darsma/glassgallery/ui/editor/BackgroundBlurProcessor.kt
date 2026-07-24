package com.darsma.glassgallery.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

internal sealed interface PortraitBlurResult {
    data class Success(val bitmap: Bitmap) : PortraitBlurResult
    data object NoPerson : PortraitBlurResult
}

/**
 * Segments people locally with ML Kit, then keeps them sharp over a blurred
 * version of the same photo. Call this from a background dispatcher.
 */
internal fun applyPortraitBackgroundBlur(source: Bitmap): PortraitBlurResult {
    val processingBitmap = source.scaledToMaxDimension(ProcessingMaxDimension)
    val options = SelfieSegmenterOptions.Builder()
        .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
        .enableRawSizeMask()
        .build()
    val segmenter = Segmentation.getClient(options)

    val mask = try {
        val inputImage = InputImage.fromBitmap(processingBitmap, 0)
        Tasks.await(segmenter.process(inputImage))
    } finally {
        segmenter.close()
    }

    val confidences = mask.readConfidences()
    if (!confidences.containsPerson()) return PortraitBlurResult.NoPerson

    val blurred = processingBitmap.createStackBlurredBitmap()
    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)

    // Let Canvas perform the memory-efficient, filtered upscale of the blurred
    // background. Rows are then blended in place with the full-resolution source.
    Canvas(output).drawBitmap(
        blurred,
        null,
        Rect(0, 0, source.width, source.height),
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
    )

    val sharpRow = IntArray(source.width)
    val blurredRow = IntArray(source.width)
    val outputRow = IntArray(source.width)
    val maskXs = FloatArray(source.width) { x ->
        coordinateInMask(x, source.width, mask.width)
    }
    for (y in 0 until source.height) {
        source.getPixels(sharpRow, 0, source.width, 0, y, source.width, 1)
        output.getPixels(blurredRow, 0, source.width, 0, y, source.width, 1)

        val maskY = coordinateInMask(y, source.height, mask.height)
        for (x in 0 until source.width) {
            val confidence = confidences.bilinearSample(
                mask.width,
                mask.height,
                maskXs[x],
                maskY,
            )
            val foregroundWeight = smoothStep(
                edge0 = ForegroundFeatherStart,
                edge1 = ForegroundFeatherEnd,
                value = confidence,
            )
            outputRow[x] = blendColors(
                foreground = sharpRow[x],
                background = blurredRow[x],
                foregroundWeight = foregroundWeight,
            )
        }
        output.setPixels(outputRow, 0, source.width, 0, y, source.width, 1)
    }

    return PortraitBlurResult.Success(output)
}

private fun SegmentationMask.readConfidences(): FloatArray {
    val pixelCount = width * height
    require(width > 0 && height > 0) { "ML Kit returned an empty segmentation mask" }

    val maskBuffer = buffer.duplicate().order(ByteOrder.nativeOrder())
    maskBuffer.rewind()
    require(maskBuffer.remaining() >= pixelCount * java.lang.Float.BYTES) {
        "ML Kit segmentation mask buffer is smaller than its dimensions"
    }

    return FloatArray(pixelCount) {
        maskBuffer.getFloat().let { confidence ->
            if (confidence.isFinite()) confidence.coerceIn(0f, 1f) else 0f
        }
    }
}

private fun FloatArray.containsPerson(): Boolean {
    var highConfidencePixels = 0
    var highestConfidence = 0f
    for (confidence in this) {
        if (confidence >= PersonPixelThreshold) highConfidencePixels++
        if (confidence > highestConfidence) highestConfidence = confidence
    }
    val minimumPersonPixels = max(MinimumPersonPixels, size / MinimumPersonAreaDivisor)
    return highestConfidence >= PersonPeakThreshold &&
        highConfidencePixels >= minimumPersonPixels
}

/**
 * Three separable box passes closely approximate a Gaussian blur while keeping
 * processing predictable on large photos. The work bitmap is capped at 1024 px.
 */
private fun Bitmap.createStackBlurredBitmap(): Bitmap {
    val width = width
    val height = height
    var sourcePixels = IntArray(width * height)
    var targetPixels = IntArray(width * height)
    getPixels(sourcePixels, 0, width, 0, 0, width, height)

    val radius = (max(width, height) / BlurRadiusDivisor).coerceIn(MinBlurRadius, MaxBlurRadius)
    repeat(BlurPasses) {
        boxBlurHorizontal(sourcePixels, targetPixels, width, height, radius)
        val swapAfterHorizontal = sourcePixels
        sourcePixels = targetPixels
        targetPixels = swapAfterHorizontal

        boxBlurVertical(sourcePixels, targetPixels, width, height, radius)
        val swapAfterVertical = sourcePixels
        sourcePixels = targetPixels
        targetPixels = swapAfterVertical
    }

    return Bitmap.createBitmap(sourcePixels, width, height, Bitmap.Config.ARGB_8888)
}

private fun boxBlurHorizontal(
    source: IntArray,
    target: IntArray,
    width: Int,
    height: Int,
    radius: Int,
) {
    val windowSize = radius * 2 + 1
    for (y in 0 until height) {
        val rowOffset = y * width
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        for (sampleX in -radius..radius) {
            val color = source[rowOffset + sampleX.coerceIn(0, width - 1)]
            alpha += Color.alpha(color)
            red += Color.red(color)
            green += Color.green(color)
            blue += Color.blue(color)
        }

        for (x in 0 until width) {
            target[rowOffset + x] = Color.argb(
                alpha / windowSize,
                red / windowSize,
                green / windowSize,
                blue / windowSize,
            )

            val outgoing = source[rowOffset + (x - radius).coerceIn(0, width - 1)]
            val incoming = source[rowOffset + (x + radius + 1).coerceIn(0, width - 1)]
            alpha += Color.alpha(incoming) - Color.alpha(outgoing)
            red += Color.red(incoming) - Color.red(outgoing)
            green += Color.green(incoming) - Color.green(outgoing)
            blue += Color.blue(incoming) - Color.blue(outgoing)
        }
    }
}

private fun boxBlurVertical(
    source: IntArray,
    target: IntArray,
    width: Int,
    height: Int,
    radius: Int,
) {
    val windowSize = radius * 2 + 1
    for (x in 0 until width) {
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        for (sampleY in -radius..radius) {
            val color = source[sampleY.coerceIn(0, height - 1) * width + x]
            alpha += Color.alpha(color)
            red += Color.red(color)
            green += Color.green(color)
            blue += Color.blue(color)
        }

        for (y in 0 until height) {
            target[y * width + x] = Color.argb(
                alpha / windowSize,
                red / windowSize,
                green / windowSize,
                blue / windowSize,
            )

            val outgoing = source[(y - radius).coerceIn(0, height - 1) * width + x]
            val incoming = source[(y + radius + 1).coerceIn(0, height - 1) * width + x]
            alpha += Color.alpha(incoming) - Color.alpha(outgoing)
            red += Color.red(incoming) - Color.red(outgoing)
            green += Color.green(incoming) - Color.green(outgoing)
            blue += Color.blue(incoming) - Color.blue(outgoing)
        }
    }
}

private fun Bitmap.scaledToMaxDimension(maxDimension: Int): Bitmap {
    val largestDimension = max(width, height)
    if (largestDimension <= maxDimension) return this
    val scale = maxDimension.toFloat() / largestDimension
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1),
        true,
    )
}

private fun coordinateInMask(sourceCoordinate: Int, sourceSize: Int, maskSize: Int): Float {
    if (sourceSize <= 1 || maskSize <= 1) return 0f
    return sourceCoordinate.toFloat() * (maskSize - 1) / (sourceSize - 1)
}

private fun FloatArray.bilinearSample(
    width: Int,
    height: Int,
    x: Float,
    y: Float,
): Float {
    val x0 = floor(x).toInt().coerceIn(0, width - 1)
    val y0 = floor(y).toInt().coerceIn(0, height - 1)
    val x1 = (x0 + 1).coerceAtMost(width - 1)
    val y1 = (y0 + 1).coerceAtMost(height - 1)
    val xWeight = (x - x0).coerceIn(0f, 1f)
    val yWeight = (y - y0).coerceIn(0f, 1f)

    val top = this[y0 * width + x0] * (1f - xWeight) +
        this[y0 * width + x1] * xWeight
    val bottom = this[y1 * width + x0] * (1f - xWeight) +
        this[y1 * width + x1] * xWeight
    return top * (1f - yWeight) + bottom * yWeight
}

private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun blendColors(
    foreground: Int,
    background: Int,
    foregroundWeight: Float,
): Int {
    val backgroundWeight = 1f - foregroundWeight
    return Color.argb(
        (Color.alpha(foreground) * foregroundWeight +
            Color.alpha(background) * backgroundWeight).roundToInt(),
        (Color.red(foreground) * foregroundWeight +
            Color.red(background) * backgroundWeight).roundToInt(),
        (Color.green(foreground) * foregroundWeight +
            Color.green(background) * backgroundWeight).roundToInt(),
        (Color.blue(foreground) * foregroundWeight +
            Color.blue(background) * backgroundWeight).roundToInt(),
    )
}

private const val ProcessingMaxDimension = 1024
private const val BlurPasses = 3
private const val BlurRadiusDivisor = 48
private const val MinBlurRadius = 6
private const val MaxBlurRadius = 24
private const val ForegroundFeatherStart = 0.18f
private const val ForegroundFeatherEnd = 0.82f
private const val PersonPixelThreshold = 0.55f
private const val PersonPeakThreshold = 0.75f
private const val MinimumPersonPixels = 16
private const val MinimumPersonAreaDivisor = 400
