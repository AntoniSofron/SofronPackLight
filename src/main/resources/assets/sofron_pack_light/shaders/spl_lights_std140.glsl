// std140 block; exact layout matches RenderPearlLightUniformPayload.java.
struct SPLLight { vec4 positionRadius; vec4 rgbPower; };
layout(std140) uniform SofronLights {
    ivec4 splLightHeader;
    SPLLight splLights[256];
};
vec3 splCalculateLight(vec3 cameraRelativePosition) {
    vec3 total = vec3(0.0);
    for (int i = 0; i < min(splLightHeader.x, 256); ++i) {
        vec4 p = splLights[i].positionRadius;
        vec4 c = splLights[i].rgbPower;
        float d = length(cameraRelativePosition - p.xyz);
        float a = pow(max(1.0 - d / max(p.w, 0.001), 0.0), 2.0);
        total += c.rgb * (c.a * a);
    }
    return total;
}
