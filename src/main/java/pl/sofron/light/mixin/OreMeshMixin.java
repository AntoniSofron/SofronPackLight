package pl.sofron.light.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.caffeinemc.mods.sodium.client.render.texture.SpriteFinderCache;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
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
        if (base == null) return bits;
        var atlas = OreMasks.current();
        if (atlas.leafDetails().contains(base)) {
            // Store the owning block's low X/Z bits even for overhanging leaf models.
            return bits | 0x80 | ((pos.getX() & 3) << 3) | ((pos.getZ() & 3) << 5);
        }
        if (!atlas.pairs().containsKey(base)) {
            // With no intermediate alpha the sprite cannot contain the alpha-254 emissive marker.
            return base.transparency().hasTranslucent() ? bits : bits | 0x10;
        }
        var level = ((LevelSliceAccessor)(Object)slice).sofron$level();
        pl.sofron.light.emissive.OreFades.observe(level.getLightEngine(),pos);
        // The three parity bits identify the block owning a vertex on a shared face.
        return bits | (1 << 3) | ((pos.getX() & 1) << 4)
            | ((pos.getY() & 1) << 5) | ((pos.getZ() & 1) << 6);
    }

    @Redirect(method = "bufferQuad", at = @At(value = "INVOKE", target =
        "Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/buffers/ChunkModelBuilder;addSprite(Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
    private void sofron$animatePairedMask(ChunkModelBuilder builder, TextureAtlasSprite base) {
        builder.addSprite(base);
        var mask = OreMasks.current().pairs().get(base);
        if (mask != null) builder.addSprite(mask);
    }
}
