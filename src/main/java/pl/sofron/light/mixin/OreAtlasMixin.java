package pl.sofron.light.mixin;

import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.sofron.light.emissive.OreMasks;

@Mixin(TextureAtlas.class)
abstract class OreAtlasMixin {
    @Inject(method = "upload", at = @At("TAIL"))
    private void sofron$collectOreMasks(SpriteLoader.Preparations atlas, CallbackInfo ci) {
        if (((TextureAtlas)(Object)this).location().equals(TextureAtlas.LOCATION_BLOCKS))
            OreMasks.rebuild(atlas);
    }
}
