package pl.sofron.light.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteFinderCache;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.LightLayer;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import pl.sofron.light.emissive.OreMasks;

@Mixin(value = BlockRenderer.class, remap = false)
abstract class OreMeshMixin extends AbstractBlockRenderContext {
    @Redirect(method = "bufferQuad", at = @At(value = "INVOKE", target =
        "Lnet/caffeinemc/mods/sodium/client/render/chunk/terrain/material/Material;bits()I"))
    private int sofron$oreFaceLight(Material material, MutableQuadViewImpl quad, float[] ao, Material original) {
        int bits = material.bits();
        var base = quad.sprite(SpriteFinderCache.forBlockAtlas());
        if (base == null || !OreMasks.current().pairs().containsKey(base)) return bits;
        // Bits 0..2 belong to Sodium. One illuminated side reveals the whole block.
        int sky = 0;
        for (Direction side : Direction.values()) {
            var outside = pos.relative(side);
            if (slice.getBrightness(LightLayer.BLOCK, outside) > 5) return bits | (17 << 3);
            sky = Math.max(sky, slice.getBrightness(LightLayer.SKY, outside));
        }
        return bits | ((1 + sky) << 3);
    }

    @Redirect(method = "bufferQuad", at = @At(value = "INVOKE", target =
        "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/buffers/ChunkModelBuilder;addSprite(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
    private void sofron$animatePairedMask(ChunkModelBuilder builder, TextureAtlasSprite base) {
        builder.addSprite(base);
        var mask = OreMasks.current().pairs().get(base);
        if (mask != null) builder.addSprite(mask);
    }
}
