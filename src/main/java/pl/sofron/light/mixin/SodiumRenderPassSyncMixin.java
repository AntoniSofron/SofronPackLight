package pl.sofron.light.mixin;

import com.mojang.renderpearl.api.commands.RenderPass;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.gpu.device.batch.MultiDrawBatch;
import net.caffeinemc.mods.sodium.client.gpu.device.context.DrawContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.sofron.light.render.gpu.SodiumRenderPassCoordinator;

/**
 * Synchronization marker at Sodium 0.9.2's actual batch draw call.
 * Verified call site from the supplied sodium-fabric-0.9.2+mc26.3.jar.
 * Does not perform unsafe glUniform calls against a potentially deferred pipeline.
 */
@Mixin(value = DefaultChunkRenderer.class, remap = false)
public abstract class SodiumRenderPassSyncMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target =
        "Lnet/caffeinemc/mods/sodium/client/gpu/device/batch/MultiDrawBatch;draw(Lnet/caffeinemc/mods/sodium/client/gpu/device/context/DrawContext;)V"),
        remap = false, require = 0)
    private void sofron$beforeBatchDraw(
        net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices matrices,
        net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable lists,
        net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass pass,
        net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform camera,
        net.caffeinemc.mods.sodium.client.util.FogParameters fog, boolean sort,
        RenderPass renderPass,
        com.mojang.renderpearl.api.textures.GpuSampler sampler,
        com.mojang.renderpearl.api.buffers.GpuBufferSlice transforms,
        com.mojang.renderpearl.api.buffers.GpuBuffer uniformBuffer,
        net.minecraft.client.renderer.oit.OitStage oitStage,
        CallbackInfo ci) {
        SodiumRenderPassCoordinator.beforeDraw(renderPass, camera);
    }
}
