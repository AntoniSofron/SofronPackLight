package pl.sofron.light.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.renderer.oit.OitStage;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.sofron.light.render.gpu.SodiumGpuLightBridge;

/** Sodium 0.9.2 for MC 26.3. Uploads RGB sources before the terrain draw. */
@Mixin(value = DefaultChunkRenderer.class, remap = false)
public abstract class SodiumChunkRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), remap = false)
    private void sofron$uploadBeforeTerrain(ChunkRenderMatrices matrices,
        ChunkRenderListIterable lists, TerrainRenderPass pass, CameraTransform camera,
        FogParameters fog, boolean sort, RenderPass renderPass, GpuSampler sampler,
        GpuBufferSlice transforms, GpuBuffer uniformBuffer, OitStage oitStage,
        CallbackInfo ci) {
        // Native RenderPearl path updates at the pre-draw hook; no competing GL uploads.
    }
}
