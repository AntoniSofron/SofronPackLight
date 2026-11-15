package pl.sofron.light.mixin;

import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.LightLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.sofron.light.emissive.OreFades;

@Mixin(ClientChunkCache.class)
abstract class OreLightUpdateMixin {
    @Inject(method = "onLightUpdate", at = @At("RETURN"))
    private void sofron$updateOreLight(LightLayer layer, SectionPos pos, CallbackInfo ci) {
        OreFades.lightChanged(((ClientChunkCache)(Object)this).getLightEngine(),pos);
    }
}
