package com.darsma.glassgallery.ui.components

import android.annotation.SuppressLint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import com.darsma.glassgallery.ui.theme.LIQUID_GLASS_AGSL

@Composable
fun Modifier.liquidGlass(): Modifier {
    var w by remember { mutableFloatStateOf(100f) }
    var h by remember { mutableFloatStateOf(100f) }

    val runtimeShader: android.graphics.RuntimeShader? = remember {
        if (Build.VERSION.SDK_INT >= 33) {
            @SuppressLint("NewApi")
            android.graphics.RuntimeShader(LIQUID_GLASS_AGSL)
        } else null
    }

    return this
        .onSizeChanged { size ->
            w = size.width.toFloat()
            h = size.height.toFloat()
            if (Build.VERSION.SDK_INT >= 33 && runtimeShader != null) {
                @SuppressLint("NewApi")
                runtimeShader.setFloatUniform("size", w, h)
            }
        }
        .graphicsLayer {
            renderEffect = if (Build.VERSION.SDK_INT >= 33 && runtimeShader != null) {
                buildApi33Effect(runtimeShader).asComposeRenderEffect()
            } else {
                RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP)
                    .asComposeRenderEffect()
            }
        }
}

@RequiresApi(33)
private fun buildApi33Effect(rs: android.graphics.RuntimeShader): RenderEffect {
    val blur   = RenderEffect.createBlurEffect(24f, 24f, Shader.TileMode.CLAMP)
    val shader = RenderEffect.createRuntimeShaderEffect(rs, "content")
    return RenderEffect.createChainEffect(blur, shader)
}
