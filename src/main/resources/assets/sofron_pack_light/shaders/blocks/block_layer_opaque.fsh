#version 460 core

#include <sodium:globals.glsl>
#include <sodium:fog.glsl>
#include <sodium:chunk_material.glsl>
#include <minecraft:oit.glsl>

layout(location = 0) in vec4 v_Color; // The interpolated vertex color
layout(location = 1) in vec2 v_TexCoord; // The interpolated block texture coordinates
layout(location = 2) in vec2 v_FragDistance; // The fragment's distance from the camera (cylindrical and spherical)
layout(location = 3) in float fadeFactor;

layout(location = 4) in vec3 spl_SurfaceCameraPos;
layout(location = 5) in float spl_Sunlit;
layout(location = 6) in vec2 spl_LightCoord;
#ifndef OIT_ALPHA_ONLY
uniform sampler2D u_LightTex;
#endif
layout(location = 7) in vec3 spl_UnlitVertexColor;
layout(location = 8) flat in int spl_OreLight;
layout(std140) uniform SofronLights {
    ivec4 splLightHeader;
    vec4 splCameraFractionAndDarken;
    vec4 splPlayerRelative;
    vec4 splAtlasCamera;
    vec4 splVoxelCamera;
};
layout(location = 9) in vec4 splStationaryVertex;
layout(location = 10) flat in float splOreOpacity;
layout(location = 11) in vec2 splMaskUV;
uniform isamplerBuffer SofronVoxels;
int voxelWord(int i) { return texelFetch(SofronVoxels, i).r; }
// Packed dynamic voxel volume, appended to the stationary light atlas.
// Layout: [originX,originY,originZ,size] + packed level/R/G/B per voxel.
// Sampling is centered on the voxel centers (block center = floor(world)+0.5).
// Treat out-of-volume cells as zero, avoiding ghost light at the volume edge.
vec4 spl_voxelFetch(int offset, int side, ivec3 q) {
    if(any(lessThan(q,ivec3(0))) || any(greaterThanEqual(q,ivec3(side))))
        return vec4(0.0);
    uint v=uint(voxelWord(offset+4+(q.y*side+q.z)*side+q.x));
    return vec4(vec3(float((v>>16u)&255u),float((v>>8u)&255u),float(v&255u))/255.0,
                float((v>>24u)&255u)/15.0);
}
// 0.9.53: pick a stable AIR CELL using block-face plane coordinates,
// not fragment derivatives. This avoids triangle-dependent face orientation.
// The voxel field is a *dynamic* light-level supplement; Minecraft's
// original lightmap and vertex shading remain the base rendering.
vec4 spl_evalDynamicSurface(vec3 cameraRelativePos) {
    int offset = 0;
    const int side = 33;
    vec3 world = cameraRelativePos + splVoxelCamera.xyz;
    // 0.9.54: determine the outward visible surface normal from the CURRENT
    // rasterized triangle, instead of comparing against the PREVIOUS frame's
    // camera position stored in the atlas. That old camera coordinate jumps
    // across face planes during vertical movement and produces dark bands.
    // Screen-space derivatives are evaluated for the current draw; normalize
    // and orient the normal towards the live camera-relative fragment vector.
    vec3 dx = dFdx(cameraRelativePos);
    vec3 dy = dFdy(cameraRelativePos);
    if (any(lessThan(world, vec3(-2))) || any(greaterThan(world, vec3(side+2)))) return vec4(0.0);
    vec3 geometric = cross(dx, dy);
    float normalSq = dot(geometric, geometric);
    if (normalSq < 1e-16) return vec4(0.0);
    geometric *= inversesqrt(normalSq);
    if (dot(geometric, -cameraRelativePos) < 0.0) geometric = -geometric;
    vec3 absNormal = abs(geometric);
    int axis = absNormal.x >= absNormal.y && absNormal.x >= absNormal.z ? 0 :
               (absNormal.y >= absNormal.z ? 1 : 2);
    // A cube face lives on an integer plane. Use the world-space geometric
    // orientation only for the NORMAL sign, never previous-frame camera data.
    // Quantize the plane to prevent tiny floating point drifts from switching
    // between neighboring cells on the same surface.
    float plane = floor(world[axis] + 0.5);
    float signSide = geometric[axis] >= 0.0 ? 1.0 : -1.0;
    vec3 airPoint = world;
    airPoint[axis] = plane + signSide * 0.51;
    vec3 centered = airPoint - vec3(0.5);
    ivec3 q = ivec3(floor(centered));
    q[axis] = int(floor(centered[axis] + 0.5));
    vec3 t = smoothstep(vec3(0.0),vec3(1.0),fract(centered));
    int a=(axis+1)%3,b=(axis+2)%3;
    ivec3 da=ivec3(0); da[a]=1;
    ivec3 db=ivec3(0); db[b]=1;
    vec4 v00=spl_voxelFetch(offset,side,q);
    vec4 v10=spl_voxelFetch(offset,side,q+da);
    vec4 v01=spl_voxelFetch(offset,side,q+db);
    vec4 v11=spl_voxelFetch(offset,side,q+da+db);
    vec4 interp=mix(mix(v00,v10,t[a]),mix(v01,v11,t[a]),t[b]);
    return clamp(interp,0.0,1.0);
}
uniform sampler2D u_BlockTex; // The block texture

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor; // The output fragment for the color framebuffer
#endif

vec4 calculateFinalColor(vec4 color) {
    #ifdef OIT_ACCUMULATE
        color = sampleColorForAccumulation(color);
        vec4 fogColor = vec4(u_FogColor.rgb * color.a, u_FogColor.a);
    #else
        vec4 fogColor = u_FogColor;
    #endif

    #ifdef OIT_ALPHA_ONLY
    float factor = 1.0;
    #else
    float factor = fadeFactor;
    #endif

    return _linearFog(color, v_FragDistance, fogColor, u_EnvironmentFog, u_RenderFog, factor);
}
vec4 sampleNearest(sampler2D source, vec2 uv, vec2 pixelSize, vec2 du, vec2 dv, vec2 texelScreenSize) {
    // Convert our UV back up to texel coordinates and find out how far over we are from the center of each pixel
    vec2 uvTexelCoords = uv / pixelSize;
    vec2 texelCenter = round(uvTexelCoords) - 0.5f;
    vec2 texelOffset = uvTexelCoords - texelCenter;

    // Move our offset closer to the texel center based on texel size on screen
    texelOffset = (texelOffset - 0.5f) * pixelSize / texelScreenSize + 0.5f;
    texelOffset = clamp(texelOffset, 0.0f, 1.0f);

    uv = (texelCenter + texelOffset) * pixelSize;
    return textureGrad(source, uv, du, dv);
}

vec4 sampleNearest(sampler2D source, vec2 uv, vec2 pixelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);
    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    return sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);
}

// Rotated Grid Super-Sampling
vec4 sampleRGSS(sampler2D source, vec2 uv, vec2 pixelSize) {
    vec2 du = dFdx(uv);
    vec2 dv = dFdy(uv);

    vec2 texelScreenSize = sqrt(du * du + dv * dv);
    float maxTexelSize = max(texelScreenSize.x, texelScreenSize.y);

    float minPixelSize = min(pixelSize.x, pixelSize.y);

    float transitionStart = minPixelSize * 1.0;
    float transitionEnd = minPixelSize * 2.0;
    float blendFactor = smoothstep(transitionStart, transitionEnd, maxTexelSize);

    if (blendFactor == 0.0) return sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);

    float duLength = length(du);
    float dvLength = length(dv);
    float minDerivative = min(duLength, dvLength);
    float maxDerivative = max(duLength, dvLength);

    float effectiveDerivative = sqrt(minDerivative * maxDerivative);

    float mipLevelExact = max(0.0, log2(effectiveDerivative / minPixelSize));

    const vec2 offsets[4] = vec2[](
    vec2(0.125, 0.375),
    vec2(-0.125, -0.375),
    vec2(0.375, -0.125),
    vec2(-0.375, 0.125)
    );

    vec4 rgssColor = vec4(0.0);
    for (int i = 0; i < 4; ++i) {
        vec2 sampleUV = uv + offsets[i] * pixelSize;
        rgssColor += textureLod(source, sampleUV, mipLevelExact);
    }
    rgssColor *= 0.25;

    if (blendFactor == 1.0) return rgssColor;

    vec4 nearestColor = sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);

    return mix(nearestColor, rgssColor, blendFactor);
}

void main() {
    vec4 color = u_UseRGSS ? sampleRGSS(u_BlockTex, v_TexCoord, u_TexelSize) : sampleNearest(u_BlockTex, v_TexCoord, u_TexelSize);
    vec3 surfaceAlbedo = color.rgb * spl_UnlitVertexColor;
    color *= v_Color; // Apply per-vertex color modulator

#ifdef ALPHA_CUTOUT
    if (color.a < ALPHA_CUTOUT) {
        discard;
    }
#endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
    #else
#ifndef SOFRON_STATIONARY_ONLY
    if (spl_OreLight != 0 && splOreOpacity == 0.0 && splAtlasCamera.w == 0.0 && splLightHeader.z == 0) {
        fragColor = calculateFinalColor(color);
        return;
    }
#endif
    ivec2 splTextureSize = textureSize(u_BlockTex, 0);
    float splEmissivePixel = 0.0;
    vec3 splUnlitTexel = vec3(0.0);
    if (spl_OreLight == 0) {
        ivec2 pixel = clamp(ivec2(floor(v_TexCoord * vec2(splTextureSize))), ivec2(0), splTextureSize - 1);
        vec4 raw = texelFetch(u_BlockTex, pixel, 0);
        splEmissivePixel = 1.0 - step(0.45, abs(raw.a * 255.0 - 254.0));
        splUnlitTexel = raw.rgb;
    }
#ifndef SOFRON_STATIONARY_ONLY
    float splFullbrightPreserve = splEmissivePixel;
    // Placed block lights: COLORIZE existing light, never add a second beam.

    vec4 splStationarySample=splStationaryVertex;
    vec3 splStationary=splStationarySample.rgb;
    float stationaryEnergy=splStationarySample.a;
    float daylightMask = 1.0 - smoothstep(0.38, 0.85, spl_Sunlit);
    // 0.9.36 diagnostic output uses the *same* chunk-atlas sample as normal shading.
    // raw: RGB contribution, strength: magnitude, mask: daylight/emissive suppression.
    // These modes intentionally replace terrain colors and bypass emissive preservation.
    if (splLightHeader.z != 0) {
        if (splLightHeader.z == 1) {
            color.rgb = splStationary * smoothstep(0.0, 0.20, stationaryEnergy);
        } else if (splLightHeader.z == 2) {
            float strength = smoothstep(0.0, 0.38, stationaryEnergy);
            color.rgb = vec3(strength);
        } else if (splLightHeader.z == 3) {
            color.rgb = vec3(daylightMask * (1.0 - splFullbrightPreserve));
        }
        fragColor = calculateFinalColor(vec4(color.rgb, color.a));
        return;
    }
    // 0.9.53: Vanilla block-light sampling remains the authority for what
    // is already lit. Moving emitters supplement only missing light.
    // Crucially we DO NOT discard baked vertex tint/AO or construct a second
    // independently shaded surface: doing so exposed triangle seams.
    if (splAtlasCamera.w > 0.0) {
        vec4 vox = spl_evalDynamicSurface(spl_SurfaceCameraPos);
        float movingMask=(1.0-smoothstep(0.22,0.70,spl_Sunlit))*(1.0-splFullbrightPreserve);
        float targetCoord = max(spl_LightCoord.x, (vox.a * 15.0 + 0.5) / 16.0);
        if (targetCoord > spl_LightCoord.x && movingMask > 0.0) {
            vec3 oldLight = textureLod(u_LightTex, spl_LightCoord, 0.0).rgb;
            vec3 newLight = textureLod(u_LightTex, vec2(targetCoord, spl_LightCoord.y), 0.0).rgb;
            float chroma = max(max(vox.r, vox.g), vox.b);
            vec3 hue = mix(vec3(1.0), vox.rgb / max(chroma, 0.0001), clamp(chroma, 0.0, 1.0));
            color.rgb += surfaceAlbedo * max(newLight - oldLight, vec3(0.0)) * hue * movingMask;
        }
    }
#endif
    // Emissive is self-lit: preserve its unlightmapped base texture color
    // after BOTH stationary tint and moving light. Never brighten neighbors.
    color.rgb = mix(color.rgb, splUnlitTexel * spl_UnlitVertexColor, splEmissivePixel);
    if (splOreOpacity > 0.0) {
        vec4 overlay = texelFetch(u_BlockTex, ivec2(splMaskUV * vec2(splTextureSize)), 0);
        color.rgb = mix(color.rgb, overlay.rgb * spl_UnlitVertexColor, overlay.a * splOreOpacity);
    }
    fragColor = calculateFinalColor(color);
    #endif
}
