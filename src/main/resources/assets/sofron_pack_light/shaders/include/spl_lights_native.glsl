// Matches RenderPearlLightUniformPayload: 16-byte ivec4 + 256 x (two vec4).
// This include is a reference; the terrain fragment shader currently declares
// an equivalent block inline, so do not include both in one shader.
struct SPLNativeLight {
    vec4 positionRadius;
    vec4 rgbPower;
};
layout(std140) uniform SofronLights {
    ivec4 spl_Header;
    SPLNativeLight spl_Lights[256];
};
vec3 spl_evaluateNative(vec3 cameraRelativePosition) {
    vec3 color = vec3(0.0);
    for (int i = 0; i < min(spl_Header.x, 256); ++i) {
        vec4 p = spl_Lights[i].positionRadius;
        vec4 c = spl_Lights[i].rgbPower;
        float attenuation = pow(max(0.0, 1.0 - length(cameraRelativePosition - p.xyz) / max(p.w, 0.001)), 2.0);
        color += c.rgb * c.a * attenuation;
    }
    return color;
}
