package com.darsma.glassgallery.ui.components

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlin.math.max
import kotlin.math.sin

/**
 * Decorative optical light for translucent chrome. API 33+ uses AGSL; API
 * 31/32 receives a gradient fallback. This never applies RenderEffect and
 * never samples or blurs photo, video, icon, or text content.
 */
@Composable
fun Modifier.opticalGlass(
    intensity: Float = 0.72f,
    light: Offset = Offset(0.18f, 0.06f),
    animated: Boolean = true,
): Modifier {
    // `animated = false` must be genuinely static. The earlier 24-hour tween
    // still invalidated every card on every display frame, even though the
    // visual movement was imperceptible. Removing that clock is especially
    // important for large galleries and for keeping text input responsive.
    val phase = if (animated) {
        val transition = rememberInfiniteTransition(label = "optical-glass-clock")
        val animatedPhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 9_600, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "optical-glass-phase",
        )
        animatedPhase
    } else {
        0.42f
    }
    val program = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        remember { runCatching { OpticalProgram() }.getOrNull() }
    } else null
    val safeIntensity = intensity.coerceIn(0f, 1.4f)

    return this.drawWithContent {
        drawContent()
        if (safeIntensity <= 0.001f || size.minDimension <= 0f) return@drawWithContent
        if (program != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val drawn = runCatching {
                drawIntoCanvas { canvas ->
                    program.draw(
                        canvas = canvas.nativeCanvas,
                        width = size.width,
                        height = size.height,
                        time = phase,
                        lightX = light.x.coerceIn(-0.25f, 1.25f),
                        lightY = light.y.coerceIn(-0.25f, 1.25f),
                        intensity = safeIntensity,
                    )
                }
            }.isSuccess
            if (!drawn) drawOpticalFallback(light, safeIntensity, phase)
        } else {
            drawOpticalFallback(light, safeIntensity, phase)
        }
    }
}

/** Finger-following specular lens for cards; only active while pressed. */
fun Modifier.touchLens(center: Offset?, intensity: Float): Modifier = drawWithContent {
    drawContent()
    val p = intensity.coerceIn(0f, 1f)
    val point = center ?: return@drawWithContent
    if (p <= 0.001f || size.minDimension <= 0f) return@drawWithContent

    val c = Offset(
        x = point.x.coerceIn(0f, 1f) * size.width,
        y = point.y.coerceIn(0f, 1f) * size.height,
    )
    val radius = max(size.width, size.height) * (0.54f + 0.16f * p)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to Color.White.copy(alpha = 0.19f * p),
                0.22f to Color(0xFFC7B4FF).copy(alpha = 0.105f * p),
                0.52f to Color(0xFF77DFFF).copy(alpha = 0.045f * p),
                1.00f to Color.Transparent,
            ),
            center = c,
            radius = radius,
        ),
        center = c,
        radius = radius,
    )
    val opposite = Offset(
        x = (size.width - c.x) * 0.82f + size.width * 0.09f,
        y = (size.height - c.y) * 0.82f + size.height * 0.09f,
    )
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFFFF8CCB).copy(alpha = 0.055f * p),
                Color.Transparent,
            ),
            center = opposite,
            radius = radius * 0.62f,
        ),
        center = opposite,
        radius = radius * 0.62f,
    )
}

private fun DrawScope.drawOpticalFallback(light: Offset, intensity: Float, time: Float) {
    val lx = (light.x + (time - 0.5f) * 0.08f).coerceIn(-0.2f, 1.2f)
    val ly = (light.y + sin(time * 6.28318f) * 0.035f).coerceIn(-0.2f, 1.2f)
    val center = Offset(size.width * lx, size.height * ly)
    val radius = max(size.width, size.height) * 0.92f
    drawRect(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to Color.White.copy(alpha = 0.105f * intensity),
                0.18f to Color(0xFFBCEEFF).copy(alpha = 0.050f * intensity),
                0.48f to Color(0xFFC1A6FF).copy(alpha = 0.025f * intensity),
                1.00f to Color.Transparent,
            ),
            center = center,
            radius = radius,
        ),
    )
    val sweepX = (time * 1.45f - 0.22f) * size.width
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                Color.Transparent,
                Color.White.copy(alpha = 0.028f * intensity),
                Color(0xFF8DE5FF).copy(alpha = 0.020f * intensity),
                Color.Transparent,
            ),
            start = Offset(sweepX, -size.height * 0.2f),
            end = Offset(sweepX + size.width * 0.34f, size.height * 1.2f),
        ),
    )
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class OpticalProgram {
    private val runtimeShader = RuntimeShader(OPTICAL_GLASS_AGSL)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = runtimeShader }

    fun draw(
        canvas: android.graphics.Canvas,
        width: Float,
        height: Float,
        time: Float,
        lightX: Float,
        lightY: Float,
        intensity: Float,
    ) {
        runtimeShader.setFloatUniform("resolution", width, height)
        runtimeShader.setFloatUniform("time", time)
        runtimeShader.setFloatUniform("light", lightX, lightY)
        runtimeShader.setFloatUniform("intensity", intensity)
        canvas.drawRect(0f, 0f, width, height, paint)
    }
}

private const val OPTICAL_GLASS_AGSL = """
    uniform float2 resolution;
    uniform float time;
    uniform float2 light;
    uniform float intensity;

    half4 main(float2 fragCoord) {
        float2 safeResolution = max(resolution, float2(1.0));
        float2 uv = fragCoord / safeResolution;
        float aspect = safeResolution.x / safeResolution.y;
        float2 q = float2((uv.x - light.x) * aspect, uv.y - light.y);
        float dist = length(q);
        float phase = time * 6.2831853;
        float waveA = sin((uv.x * 6.0 + uv.y * 3.5) + phase);
        float waveB = sin((uv.x * -4.0 + uv.y * 7.0) - phase * 0.73);
        float caustic = pow(max(0.0, 0.5 + 0.5 * (waveA * 0.58 + waveB * 0.42)), 7.0);
        caustic *= smoothstep(1.15, 0.02, dist);
        float key = smoothstep(0.80, 0.0, dist);
        float diagonal = smoothstep(0.075, 0.0, abs((uv.x - uv.y * 0.42) - (time * 1.35 - 0.18)));
        float topRim = smoothstep(0.17, 0.0, uv.y) * smoothstep(1.1, 0.0, dist);
        float alpha = (key * 0.040 + caustic * 0.060 + diagonal * 0.034 + topRim * 0.030) * intensity;
        half3 cool = half3(0.58, 0.88, 1.0);
        half3 violet = half3(0.78, 0.60, 1.0);
        half3 rose = half3(1.0, 0.50, 0.78);
        half mixAmount = half(0.5 + 0.5 * sin(phase + uv.x * 4.0));
        half3 spectrum = mix(cool, violet, mixAmount);
        spectrum = mix(spectrum, rose, half(caustic * 0.22));
        half3 rgb = mix(half3(1.0), spectrum, half(0.48 + caustic * 0.26));
        return half4(rgb * half(alpha), half(alpha));
    }
"""
