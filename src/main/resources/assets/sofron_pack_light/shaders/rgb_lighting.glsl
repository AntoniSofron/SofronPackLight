// GLSL 330. Light data is two RGBA32F texels per light.
// This file must be included in the actual fragment shader by the Sodium adapter.
#ifndef SOFRON_RGB_LIGHTING
#define SOFRON_RGB_LIGHTING
#define SPL_MAX_LIGHTS 256
uniform samplerBuffer splLightData;
uniform int splLightCount;
uniform float splIntensity;

vec3 sofronDynamicLighting(vec3 cameraRelativeSurfacePosition) {
    vec3 result = vec3(0.0);
    int n = clamp(splLightCount, 0, SPL_MAX_LIGHTS);
    for (int i = 0; i < n; ++i) {
        vec4 positionRadius = texelFetch(splLightData, i * 2);
        vec4 colorPower = texelFetch(splLightData, i * 2 + 1);
        float radius = max(positionRadius.w, 0.001);
        float distanceToLight = length(cameraRelativeSurfacePosition - positionRadius.xyz);
        float fade = max(0.0, 1.0 - distanceToLight / radius);
        result += colorPower.rgb * (fade * fade * colorPower.a * splIntensity);
    }
    return result;
}
// Not a shadow solution. Do not add this term to the terrain output until
// its camera-space coordinates and linear-color handling are verified.
#endif
