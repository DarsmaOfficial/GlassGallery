package com.darsma.glassgallery.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import org.intellij.lang.annotations.Language

/**
 * GPU shader effects (AGSL). AGSL requires Android 13 (API 33); on anything
 * older every helper here is a no-op so the app keeps its gradient-based look.
 *
 * Centralising the version gate means call sites never branch — they just
 * apply the modifier and get the best result the device can render.
 */
object GlassShader {
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

/**
 * A drifting diagonal sheen — a soft band of light that travels slowly across
 * a glass surface, the way a highlight slides over real frosted glass when you
 * tilt it. Animated entirely on the GPU.
 */
@Language("AGSL")
private const val SHEEN_AGSL = """
uniform float2 size;
uniform float  time;

half4 main(float2 coord) {
    float2 uv = coord / size;
    // A diagonal coordinate that the band sweeps along.
    float diag = (uv.x + uv.y) * 0.5;
    // The band travels and wraps every cycle.
    float travel = fract(time * 0.08);
    float d = abs(diag - travel);
    d = min(d, 1.0 - d);
    // Soft falloff — a gentle highlight, never a hard line.
    float band = smoothstep(0.16, 0.0, d);
    float alpha = band * 0.10;
    return half4(alpha, alpha, alpha, alpha);
}
"""

/**
 * Overlays a slow, GPU-rendered light sheen on a glass surface. No-op below
 * API 33. The sheen is additive and very subtle — richness, not distraction.
 */
@Composable
fun Modifier.glassSheen(): Modifier {
    if (!GlassShader.supported) return this
    return this.sheen33()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.sheen33(): Modifier {
    val shader = remember { RuntimeShader(SHEEN_AGSL) }
    var time by remember { mutableFloatStateOf(0f) }

    // Advance shader time once per frame — smooth at the display's refresh rate.
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) {
            withFrameMillis { now ->
                time = (now - start) / 1000f
            }
        }
    }

    return this.graphicsLayer {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("time", time)
        renderEffect = RenderEffect
            .createRuntimeShaderEffect(shader, "contents")
            .asComposeRenderEffect()
    }
}

/**
 * A real frosted-glass backdrop blur. Unlike a gradient tint, this samples the
 * pixels behind the surface and blurs them — true depth. No-op below API 33.
 *
 * @param radius blur strength in pixels.
 */
@Composable
fun Modifier.frostedBlur(radius: Float = 24f): Modifier {
    if (!GlassShader.supported) return this
    return this.graphicsLayer {
        renderEffect = RenderEffect
            .createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
    }
}

/**
 * An expanding ring-ripple distortion, centred on a point, driven by a 0..1
 * progress value. Used for the double-tap seek flourish. No-op below API 33.
 */
@Language("AGSL")
private const val RIPPLE_AGSL = """
uniform shader contents;
uniform float2 size;
uniform float2 center;
uniform float  progress;

half4 main(float2 coord) {
    float2 uv = coord / size;
    float2 c  = center / size;
    float dist = distance(uv, c);
    // A ring that expands outward as progress grows.
    float ring = progress * 0.9;
    float band = smoothstep(0.06, 0.0, abs(dist - ring));
    // Displace pixels along the radial direction at the ring.
    float2 dir = normalize(coord - center + float2(0.001, 0.001));
    float push = band * (1.0 - progress) * 26.0;
    float2 displaced = coord + dir * push;
    return contents.eval(displaced);
}
"""

/**
 * Applies an animated GPU ripple distortion radiating from [center].
 * [progress] runs 0 (just tapped) to 1 (ripple dissipated).
 */
@Composable
fun Modifier.seekRipple(
    centerX: Float,
    centerY: Float,
    progress: Float,
): Modifier {
    if (!GlassShader.supported || progress <= 0f || progress >= 1f) return this
    return this.rippleEffect33(centerX, centerY, progress)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun Modifier.rippleEffect33(
    centerX: Float,
    centerY: Float,
    progress: Float,
): Modifier {
    val shader = remember { RuntimeShader(RIPPLE_AGSL) }
    return this.graphicsLayer {
        shader.setFloatUniform("size", size.width, size.height)
        shader.setFloatUniform("center", centerX, centerY)
        shader.setFloatUniform("progress", progress)
        renderEffect = RenderEffect
            .createRuntimeShaderEffect(shader, "contents")
            .asComposeRenderEffect()
    }
}
