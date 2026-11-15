#version 330
#extension GL_ARB_separate_shader_objects : require

#include <sodium:globals.glsl>
#include <sodium:fog.glsl>
#include <sodium:chunk_vertex.glsl>

layout(location = 0) out vec4 v_Color;
layout(location = 1) out vec2 v_TexCoord;

#ifdef USE_FOG
layout(location = 2) out vec2 v_FragDistance;
layout(location = 3) out float fadeFactor;
#endif

layout(location = 4) out vec3 spl_SurfaceCameraPos;
layout(location = 5) out float spl_Sunlit;
// Raw block-lightmap coordinate: maximum block-light level denotes fullbright.
layout(location = 6) out vec2 spl_LightCoord;
layout(location = 7) out vec3 spl_UnlitVertexColor;
layout(location = 8) flat out int spl_OreLight;
uniform isamplerBuffer u_SectionTimeInfo;

#ifdef VULKAN
layout(push_constant) uniform PC {
    vec3 u_RegionOffset;
    int u_CurrentTime;
    uint u_RegionID;
};
#else
uniform vec3 u_RegionOffset;
uniform int u_CurrentTime;
uniform uint u_RegionID;
#endif

#ifndef OIT_ALPHA_ONLY
uniform sampler2D u_LightTex; // The light map texture sampler
#endif

uvec3 _get_relative_chunk_coord(uint pos) {
    // Packing scheme is defined by LocalSectionIndex
    return uvec3(pos) >> uvec3(5u, 0u, 2u) & uvec3(7u, 3u, 7u);
}

vec3 _get_draw_translation(uint pos) {
    return _get_relative_chunk_coord(pos) * vec3(16.0);
}

layout(std140) uniform SofronLights {
    ivec4 splLightHeader;
    vec4 splCameraFractionAndDarken;
    vec4 splPlayerRelative;
    vec4 splAtlasCamera;
};
layout(location = 9) out vec4 splStationaryVertex;
uniform isamplerBuffer SofronLightAtlas;
int spl_atlas(int i) { return texelFetch(SofronLightAtlas, i).r; }
float spl_float(int i) { return intBitsToFloat(spl_atlas(i)); }
// Store energy-weighted hue during interpolation to keep dark nodes neutral.
vec4 bakedNode(int i) {
    uint packedHue = uint(spl_atlas(i));
    vec3 hue = vec3((uvec3(packedHue) >> uvec3(0u,8u,16u)) & uvec3(255u)) / 255.0;
    float energy = spl_float(i+1);
    return vec4(hue * energy, energy);
}

vec4 bakedLight(int start, vec3 p) {
    ivec3 cell = min(ivec3(p), ivec3(7));
    vec3 t = p - vec3(cell);
    int i = start + 2*((cell.z*9 + cell.y)*9 + cell.x);
    vec4 lo = mix(mix(bakedNode(i), bakedNode(i+2), t.x),
                  mix(bakedNode(i+18), bakedNode(i+20), t.x), t.y);
    i += 162;
    vec4 hi = mix(mix(bakedNode(i), bakedNode(i+2), t.x),
                  mix(bakedNode(i+18), bakedNode(i+20), t.x), t.y);
    vec4 result = mix(lo, hi, t.z);
    return vec4(result.rgb / max(result.a, 1e-10), result.a);
}

vec4 spl_evalChunkLights(vec3 cameraRelativePos) {
    int width = spl_atlas(0);
    vec3 position = cameraRelativePos + splAtlasCamera.xyz;
    ivec3 section = ivec3(floor(position / 16.0));
    if (any(lessThan(section.xz,ivec2(0))) || any(greaterThanEqual(section.xz,ivec2(width)))) return vec4(0.0);
    int column = spl_atlas(1 + section.z*width + section.x);
    if (column == 0) return vec4(0.0);
    int row = section.y - spl_atlas(column);
    if (row < 0 || row >= spl_atlas(column+1)) return vec4(0.0);
    int offset = spl_atlas(column+2+row);
    if (offset == 0) return vec4(0.0);
    int begin = column+offset+1;
    int count = spl_atlas(begin-1);
    vec3 p = position - vec3(section)*16.0;
    if (count < 0) return bakedLight(begin, p * 0.5);
    vec3 weightedHue=vec3(0.0);
    float hueWeight=0.0;
    float totalStrength=0.0;
    for(int i=0;i<count;i++) {
        int base=begin+i*8;
        vec3 lpos=vec3(spl_float(base),spl_float(base+1),spl_float(base+2));
        // Reduce ONLY the visual RGB tint radius; vanilla emission stays untouched.
        float radius=max(spl_float(base+3),0.001);
        if (dot(p-lpos,p-lpos) >= radius*radius) continue;
        float distanceFactor=clamp(1.0-distance(p,lpos)/radius,0.0,1.0);
        float influence=spl_float(base+7)*pow(distanceFactor,2.8);
        vec3 rgb=max(vec3(0.0),vec3(spl_float(base+4),spl_float(base+5),spl_float(base+6)));
        float peak=max(max(rgb.r,rgb.g),rgb.b);
        if (peak <= 0.00001 || influence <= 0.00001) continue;
        totalStrength+=influence*peak;
        // Favor nearby, dominant emitters, not the huge number of distant ones.
        float priority=influence*influence*peak;
        weightedHue+=(rgb/peak)*priority;
        hueWeight+=priority;
    }
    if (hueWeight <= 0.00001) return vec4(0.0);
    vec3 hue=weightedHue/hueWeight;
    return vec4(clamp(hue,0.0,1.0),max(totalStrength,0.0));
}

uniform isamplerBuffer SofronOreRects;
vec4 oreRect(int at) {
    return intBitsToFloat(ivec4(texelFetch(SofronOreRects, at).r,
        texelFetch(SofronOreRects, at+1).r, texelFetch(SofronOreRects, at+2).r,
        texelFetch(SofronOreRects, at+3).r));
}
uniform isamplerBuffer SofronOreFades;
int fadeWord(int i) { return texelFetch(SofronOreFades, i).r; }
float oreLightOpacity(ivec3 pos) {
    uint mask = uint(fadeWord(0));
    if (mask == 0u) return 0.0;
    uvec3 k = uvec3(pos);
    uint slot = (k.x*73856093u ^ k.y*19349663u ^ k.z*83492791u) & mask;
    for (uint probe = 0u; probe <= mask; ++probe) {
        int at = 1 + int(slot)*6;
        int state = fadeWord(at+3);
        if (state == 0) return 0.0;
        if (all(equal(pos, ivec3(fadeWord(at), fadeWord(at+1), fadeWord(at+2))))) {
            float age = splPlayerRelative.w - intBitsToFloat(fadeWord(at+4));
            return mix(intBitsToFloat(fadeWord(at+5)), state == 2 ? 1.0 : 0.0,
                       smoothstep(0.0, 0.65, age));
        }
        slot = (slot+1u) & mask;
    }
    return 0.0;
}

float oreOpacity(ivec3 block) {
    float distanceToPlayer = length(vec3(block) + 0.5 - splPlayerRelative.xyz);
    if (distanceToPlayer >= 64.0) return 0.0;
    if (distanceToPlayer <= 12.0) return 1.0;
    float nearby = 1.0 - smoothstep(12.0, 16.0, distanceToPlayer);
    float lightRange = 1.0 - smoothstep(56.0, 64.0, distanceToPlayer);
    ivec3 absoluteBlock = block + splLightHeader.xyw;
    return mix(nearby, 1.0, lightRange * oreLightOpacity(absoluteBlock));
}

layout(location = 10) flat out float splOreOpacity;
layout(location = 11) out vec2 splMaskUV;

void mapOreMask() {
    // Sodium stores UVs rounded towards the sprite interior at both edges.
    vec2 uv = _vert_tex_diffuse_coord;
    int tile = texelFetch(SofronOreRects, 0).r;
    if (tile <= 0) { splOreOpacity = 0.0; return; }
    ivec2 cell = ivec2(uv / u_TexelSize) / tile;
    int slot = 3 + 2*(cell.x + cell.y * texelFetch(SofronOreRects, 1).r);
    int begin = texelFetch(SofronOreRects, slot).r;
    int count = texelFetch(SofronOreRects, slot+1).r;
    int records = texelFetch(SofronOreRects, 2).r;
    for (int i = 0; i < count; ++i) {
        int rect = records + 8*texelFetch(SofronOreRects, begin+i).r;
        vec4 base = oreRect(rect);
        if (any(lessThan(uv, base.xy)) || any(greaterThanEqual(uv, base.zw))) continue;
        vec4 mask = oreRect(rect+4);
        splMaskUV = mix(mask.xy, mask.zw, (v_TexCoord - base.xy) / (base.zw - base.xy));
        return;
    }
    splOreOpacity = 0.0;
}

void main() {
    _vert_init();
    spl_OreLight = int((_material_params >> 3u) & 1u);

    // Transform the chunk-local vertex position into world model space
    vec3 translation = u_RegionOffset + _get_draw_translation(_draw_id);
    vec3 position = _vert_position + translation;
    if ((_material_params & 128u) != 0u) {
        vec2 low = vec2((uvec2(_material_params) >> uvec2(3u, 5u)) & 3u);
        vec2 center = floor((_vert_position.xz - low + 1.5) * 0.25) * 4.0 + low + 0.5;
        vec2 delta = center + translation.xz + splCameraFractionAndDarken.xz - splPlayerRelative.xz;
        if (dot(delta, delta) >= 128.0 * 128.0) {
            gl_Position = vec4(2.0, 0.0, 0.0, 1.0);
            return;
        }
        spl_OreLight = 2; // Ordinary leaf detail: skip the emissive sentinel.
    }
    spl_SurfaceCameraPos = position;
#ifndef OIT_ALPHA_ONLY
    splStationaryVertex = spl_evalChunkLights(position);
#else
    splStationaryVertex = vec4(0);
#endif

#ifdef USE_FOG
    v_FragDistance = getFragDistance(position);

    int chunkId = int(_draw_id);
    int chunkFade = texelFetch(u_SectionTimeInfo, int((u_RegionID * 256u) + uint(chunkId))).r;
    int fadeTime = u_CurrentTime - chunkFade;
    float elapsed = float(fadeTime);
    float fade = clamp(float(u_CurrentTime - chunkFade) * u_FadePeriodInv, 0.0, 1.0);
    fadeFactor = (chunkFade < 0) ? 1.0 : fade;
#endif

    // Transform the vertex position into model-view-projection space
    gl_Position = u_ProjectionMatrix * u_ModelViewMatrix * vec4(position, 1.0);

    // Add the light color to the vertex color, and pass the texture coordinates to the fragment shader
#ifndef OIT_ALPHA_ONLY
    spl_UnlitVertexColor = _vert_color.rgb;
    v_Color = _vert_color * texture(u_LightTex, _vert_tex_light_coord);
    spl_Sunlit = texture(u_LightTex, vec2(0.03125, _vert_tex_light_coord.y)).r;
    spl_LightCoord = _vert_tex_light_coord;
#else
    spl_UnlitVertexColor = _vert_color.rgb;
    v_Color = _vert_color;
    spl_Sunlit = 0.0;
    spl_LightCoord = vec2(0.0);
#endif

    v_TexCoord = (_vert_tex_diffuse_coord_bias * u_TexCoordShrink) + _vert_tex_diffuse_coord; // FMA for precision
    splOreOpacity = 0.0;
    splMaskUV = vec2(0.0);
#ifndef OIT_ALPHA_ONLY
    if (spl_OreLight == 1) {
        vec3 parity = vec3((uvec3(_material_params) >> uvec3(4u,5u,6u)) & uvec3(1u));
        ivec3 local = ivec3(floor((_vert_position - parity + 0.5) * 0.5) * 2.0 + parity);
        ivec3 block = ivec3(round(translation + splCameraFractionAndDarken.xyz)) + local;
        splOreOpacity = oreOpacity(block);
        if (splOreOpacity > 0.0) mapOreMask();
    }
#endif
}
