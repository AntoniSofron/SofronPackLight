package pl.sofron.light.mixin;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Replaces the first Sodium bind group during shader pipeline construction.
 * Preserves all original bindings and appends the SofronLights std140 uniform buffer.
 * Only supports Sodium 0.9.2, whose layout was inspected in bytecode.
 */
@Mixin(value = ShaderChunkRenderer.class, remap = false)
public abstract class SodiumRenderPearlLayoutMixin {
    private static final BindGroupLayout SOFRON_BIND_GROUP = BindGroupLayout.builder()
        .withUniform("u_BlockTex", UniformType.COMBINED_IMAGE_SAMPLER)
        .withUniform("u_Globals", UniformType.UNIFORM_BUFFER)
        .withUniform("u_SectionTimeInfo", UniformType.TEXEL_BUFFER,
            com.mojang.renderpearl.api.GpuFormat.R32_SINT)
        .withUniform("SofronLights", UniformType.UNIFORM_BUFFER)
        .withUniform("SofronLightAtlas", UniformType.TEXEL_BUFFER,
            com.mojang.renderpearl.api.GpuFormat.R32_SINT)
        .withUniform("SofronVoxels", UniformType.TEXEL_BUFFER, com.mojang.renderpearl.api.GpuFormat.R32_SINT)
        .withUniform("SofronOreFades", UniformType.TEXEL_BUFFER, com.mojang.renderpearl.api.GpuFormat.R32_SINT)
        .withUniform("SofronOreRects", UniformType.TEXEL_BUFFER, com.mojang.renderpearl.api.GpuFormat.R32_SINT)
        .build();

    @ModifyArg(method = {"createShader", "createOITShader"},
        at = @At(value = "INVOKE", target =
            "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;withBindGroupLayout(Lcom/mojang/renderpearl/api/pipeline/BindGroupLayout;)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;", ordinal = 0),
        index = 0, remap = false, require = 1)
    private BindGroupLayout sofron$addRgbUniform(BindGroupLayout original) {
        return SOFRON_BIND_GROUP;
    }
}
