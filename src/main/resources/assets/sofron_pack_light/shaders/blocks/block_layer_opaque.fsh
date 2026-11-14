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
// RenderPearl BindGroupLayout: SofronLights (std140, 3088 bytes).
struct SPLLight { vec4 positionRadius; vec4 rgbPower; };
layout(std140) uniform SofronLights {
    ivec4 splLightHeader;
    SPLLight splLights[256];
    vec4 splCameraFractionAndDarken;
    vec4 splPlayerRelative;
    ivec4 splLookTarget;
};
// The header separates moving emitters from placed blocks. Existing block light
// supplies its own brightness; moving lights have no vanilla lightmap entry.
vec4 spl_eval(vec3 p, int beginLight, int endLight) {
    vec3 weightedHue = vec3(0.0);
    float hueWeight = 0.0;
    float strength = 0.0;
    for (int i = beginLight; i < min(endLight, 256); ++i) {
        vec4 position = splLights[i].positionRadius;
        vec4 emission = splLights[i].rgbPower;
        float x = clamp(1.0 - distance(p, position.xyz) / max(position.w, 0.001), 0.0, 1.0);
        float influence = max(emission.a, 0.0) * pow(x, 2.8);
        vec3 rgb = max(emission.rgb, vec3(0.0));
        float peak = max(max(rgb.r, rgb.g), rgb.b);
        if (peak <= 0.00001 || influence <= 0.00001) continue;
        float priority = influence * influence * peak;
        weightedHue += (rgb / peak) * priority;
        hueWeight += priority;
        strength += influence * peak;
    }
    return hueWeight > 0.00001 ?
        vec4(clamp(weightedHue / hueWeight, 0.0, 1.0), strength) : vec4(0.0);
}
uniform isamplerBuffer SofronLightAtlas;
int spl_atlas(int i) { return texelFetch(SofronLightAtlas, i).r; }
float spl_float(int i) { return intBitsToFloat(spl_atlas(i)); }
// Keep chromaticity and strength separate. Per-channel clamping of the sum
// turned strongly overlapping RED + nearby cyan/green lights into white.
vec4 spl_evalChunkLights(vec3 cameraRelativePos) {
    if (spl_atlas(0) != 1) return vec4(0.0);
    int ox=spl_atlas(1), oz=spl_atlas(2), width=spl_atlas(3);
    if (width < 1 || width > 129) return vec4(0.0);
    vec3 p=cameraRelativePos+vec3(spl_float(4),spl_float(5),spl_float(6));
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
// Packed dynamic voxel volume, appended to the stationary light atlas.
// Layout: [originX,originY,originZ,size] + packed level/R/G/B per voxel.
// Sampling is centered on the voxel centers (block center = floor(world)+0.5).
// Treat out-of-volume cells as zero, avoiding ghost light at the volume edge.
vec4 spl_voxelFetch(int offset, int side, ivec3 q) {
    if(any(lessThan(q,ivec3(0))) || any(greaterThanEqual(q,ivec3(side))))
        return vec4(0.0);
    uint v=uint(spl_atlas(offset+4+(q.y*side+q.z)*side+q.x));
    return vec4(vec3(float((v>>16u)&255u),float((v>>8u)&255u),float(v&255u))/255.0,
                float((v>>24u)&255u)/15.0);
}
// 0.9.53: pick a stable AIR CELL using block-face plane coordinates,
// not fragment derivatives. This avoids triangle-dependent face orientation.
// The voxel field is a *dynamic* light-level supplement; Minecraft's
// original lightmap and vertex shading remain the base rendering.
vec4 spl_evalDynamicSurface(vec3 cameraRelativePos) {
    if (spl_atlas(0) != 1) return vec4(0.0);
    int offset = spl_atlas(7);
    if (offset <= 0) return vec4(0.0);
    int side = spl_atlas(offset + 3);
    if (side != 33) return vec4(0.0);
    vec3 world = cameraRelativePos + vec3(
        spl_float(4) + float(spl_atlas(1) * 16),
        spl_float(5),
        spl_float(6) + float(spl_atlas(2) * 16));
    ivec3 origin = ivec3(spl_atlas(offset), spl_atlas(offset+1), spl_atlas(offset+2));
    // 0.9.54: determine the outward visible surface normal from the CURRENT
    // rasterized triangle, instead of comparing against the PREVIOUS frame's
    // camera position stored in the atlas. That old camera coordinate jumps
    // across face planes during vertical movement and produces dark bands.
    // Screen-space derivatives are evaluated for the current draw; normalize
    // and orient the normal towards the live camera-relative fragment vector.
    vec3 dx = dFdx(cameraRelativePos);
    vec3 dy = dFdy(cameraRelativePos);
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
    vec3 centered = airPoint - vec3(origin) - vec3(0.5);
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
uniform isamplerBuffer SofronOreRects;
vec4 oreRect(int at) {
    return intBitsToFloat(ivec4(texelFetch(SofronOreRects, at).r,
        texelFetch(SofronOreRects, at+1).r, texelFetch(SofronOreRects, at+2).r,
        texelFetch(SofronOreRects, at+3).r));
}
bool oreVisible(vec3 inward) {
    // Work relative to the integer camera block to preserve precision far from spawn.
    ivec3 block = ivec3(floor(spl_SurfaceCameraPos + splCameraFractionAndDarken.xyz + inward));
    vec3 delta = vec3(block) + 0.5 - splPlayerRelative.xyz;
    return dot(delta, delta) <= 256.0 || spl_OreLight == 17 ||
        float(spl_OreLight - 1) - splCameraFractionAndDarken.w > 5.0 ||
        (splLookTarget.w != 0 && all(equal(block, splLookTarget.xyz)));
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

    vec4 nearestColor = sampleNearest(source, uv, pixelSize, du, dv, texelScreenSize);

    return mix(nearestColor, rgssColor, blendFactor);
}

void main() {
    vec4 color = u_UseRGSS ? sampleRGSS(u_BlockTex, v_TexCoord, u_TexelSize) : sampleNearest(u_BlockTex, v_TexCoord, u_TexelSize);
    // Sofron 0.9.21 paired resource pack encodes emissive pixels with alpha=254.
    // Decode from mip 0 to avoid interpolation/mipmaps erasing the sentinel.
    // The original alpha=255 and fully transparent pixels are unchanged.
    ivec2 splTextureSize = textureSize(u_BlockTex, 0);
    ivec2 splPixel = clamp(ivec2(floor(v_TexCoord * vec2(splTextureSize))), ivec2(0), splTextureSize - ivec2(1));
    float splRawAlpha = texelFetch(u_BlockTex, splPixel, 0).a;
    float splEmissivePixel = 1.0 - step(0.45, abs(splRawAlpha * 255.0 - 254.0));
    if (spl_OreLight != 0) splEmissivePixel = 0.0;
    vec3 splUnlitTexel = texelFetch(u_BlockTex, splPixel, 0).rgb;
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
    // IMPORTANT: vanilla block-light coordinate near 1.0 is NOT an emissive flag.
    // A normal ground surface inside a cluster of torches also has near-maximal
    // block light. Suppressing RGB there created the gray center/red outer ring.
    // Protect only pixels explicitly marked emissive in the paired resource pack.
    // Restore their unlit texel below after both stationary and moving lighting.
    float splFullbrightPreserve = splEmissivePixel;
    // Placed block lights: COLORIZE existing light, never add a second beam.
    int movingCount = clamp(splLightHeader.y, 0, splLightHeader.x);
    vec4 splStationarySample=spl_evalChunkLights(spl_SurfaceCameraPos);
    vec3 splStationary=splStationarySample.rgb;
    float stationaryEnergy=splStationarySample.a;
    float daylightMask = 1.0 - smoothstep(0.38, 0.85, spl_Sunlit);
    if (stationaryEnergy > 0.0001) {
        // Local tint only: vanilla lightmap is the source of brightness.
        // Normalized hue is evaluated from individual nearby emitters;
        // strength affects blend amount, not added lighting or source hue.
        vec3 sourceHue = clamp(splStationary, 0.0, 1.0);
        float blend = (1.0 - exp(-stationaryEnergy * 1.15))
                    * daylightMask * (1.0 - splFullbrightPreserve);
        vec3 base = color.rgb;
        // Highly saturated reds have low perceived luminance relative to green.
        // Give red-dominant hues a modest channel boost without changing vanilla light.
        float redDominance = clamp(sourceHue.r - max(sourceHue.g, sourceHue.b), 0.0, 1.0);
        float perceptualCompensation = 1.0 + 0.65 * redDominance;
        vec3 chromatic = min(vec3(1.0), base * sourceHue * perceptualCompensation);
        color.rgb = mix(base, chromatic, clamp(blend, 0.0, 0.92));
    }
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
    vec4 vox = spl_evalDynamicSurface(spl_SurfaceCameraPos);
    float movingMask=(1.0-smoothstep(0.22,0.70,spl_Sunlit))*(1.0-splFullbrightPreserve);
    float targetCoord = max(spl_LightCoord.x, (vox.a * 15.0 + 0.5) / 16.0);
    vec3 oldLight = texture(u_LightTex, spl_LightCoord).rgb;
    vec3 newLight = texture(u_LightTex, vec2(targetCoord, spl_LightCoord.y)).rgb;
    float chroma = max(max(vox.r, vox.g), vox.b);
    vec3 hue = mix(vec3(1.0), vox.rgb / max(chroma, 0.0001), clamp(chroma, 0.0, 1.0));
    color.rgb += surfaceAlbedo * max(newLight - oldLight, vec3(0.0)) * hue * movingMask;
    // Emissive is self-lit: preserve its unlightmapped base texture color
    // after BOTH stationary tint and moving light. Never brighten neighbors.
    color.rgb = mix(color.rgb, splUnlitTexel * spl_UnlitVertexColor, splEmissivePixel);
    if (spl_OreLight != 0) {
        vec3 n = cross(dFdx(spl_SurfaceCameraPos), dFdy(spl_SurfaceCameraPos));
        n *= inversesqrt(max(dot(n,n), 1e-20));
        if (dot(n, spl_SurfaceCameraPos) > 0.0) n = -n;
        if (oreVisible(-n * 0.00025)) {
            int pairs = texelFetch(SofronOreRects, 0).r;
            for (int i = 0; i < pairs; ++i) {
                vec4 base = oreRect(1 + 8*i);
                if (any(lessThan(v_TexCoord, base.xy)) || any(greaterThanEqual(v_TexCoord, base.zw))) continue;
                vec4 mask = oreRect(5 + 8*i);
                vec2 localUV = (v_TexCoord - base.xy) / (base.zw - base.xy);
                ivec2 maskPixel = ivec2(mix(mask.xy, mask.zw, localUV) * vec2(splTextureSize));
                vec4 overlay = texelFetch(u_BlockTex, maskPixel, 0);
                color.rgb = mix(color.rgb, overlay.rgb * spl_UnlitVertexColor, overlay.a);
                break;
            }
        }
    }
    fragColor = calculateFinalColor(color);
    #endif
}
