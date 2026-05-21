package com.darsma.glassgallery.ui.theme

const val LIQUID_GLASS_AGSL = """
uniform shader content;
uniform float2  size;

half4 main(float2 fragCoord) {
    float2 center   = size * 0.5;
    float2 delta    = fragCoord - center;
    float  normDist = length(delta) / length(center);

    float lensK  = 0.10;
    float2 refracted = center + delta * (1.0 + lensK * (1.0 - normDist * normDist));

    float  caStrength = normDist * 3.5;
    float2 caDir      = normalize(delta + float2(0.0001, 0.0001));

    float2 sR = clamp(refracted + caDir * caStrength, float2(0.0), size - 1.0);
    float2 sG = clamp(refracted,                      float2(0.0), size - 1.0);
    float2 sB = clamp(refracted - caDir * caStrength, float2(0.0), size - 1.0);

    half r = content.eval(sR).r;
    half g = content.eval(sG).g;
    half b = content.eval(sB).b;

    float fresnel = pow(clamp(normDist, 0.0, 1.0), 2.8);
    half3 col = half3(r, g, b);
    col += half3(fresnel * 0.06, fresnel * 0.09, fresnel * 0.20);
    col  = mix(col, half3(0.45h, 0.55h, 0.88h), 0.045h);

    return half4(col, 1.0);
}
"""
