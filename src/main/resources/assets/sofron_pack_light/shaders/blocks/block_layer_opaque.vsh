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
    ivec4 splLookTarget;
    vec4 splAtlasCamera;
};
layout(location = 9) out vec4 splStationaryVertex;
uniform isamplerBuffer SofronLightAtlas;
int spl_atlas(int i) { return texelFetch(SofronLightAtlas, i).r; }
float spl_float(int i) { return intBitsToFloat(spl_atlas(i)); }
// Keep chromaticity and strength separate. Per-channel clamping of the sum
// turned strongly overlapping RED + nearby cyan/green lights into white.
vec4 spl_evalChunkLights(vec3 cameraRelativePos) {
    if (spl_atlas(0) != 1) return vec4(0.0);
    int ox=spl_atlas(1), oz=spl_atlas(2), width=spl_atlas(3);
    if (width < 1 || width > 129) return vec4(0.0);
    vec3 p=cameraRelativePos+splAtlasCamera.xyz;
    ivec2 cell=ivec2(floor(p.xz / 16.0));
    if (any(lessThan(cell,ivec2(0))) || any(greaterThanEqual(cell,ivec2(width)))) return vec4(0.0);
    int entry=8+2*(cell.y*width+cell.x);
    int begin=spl_atlas(entry), count=spl_atlas(entry+1);
    if (count <= 0) return vec4(0.0);
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

void main() {
    _vert_init();
    spl_OreLight = int(_material_params >> 3u);

    // Transform the chunk-local vertex position into world model space
    vec3 translation = u_RegionOffset + _get_draw_translation(_draw_id);
    vec3 position = _vert_position + translation;
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
}
