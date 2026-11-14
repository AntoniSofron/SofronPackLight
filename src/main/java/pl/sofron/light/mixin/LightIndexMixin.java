package pl.sofron.light.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.sofron.light.SofronPackLightClient;

@Mixin(ClientLevel.class)
abstract class LightIndexMixin {
    @Inject(method = "sendBlockUpdated", at = @At("RETURN"))
    private void sofron$changed(BlockPos pos, BlockState old, BlockState state, int flags, CallbackInfo ci) {
        if (old != state && (old.getLightEmission() > 0 || state.getLightEmission() > 0))
            SofronPackLightClient.LIGHTS.chunkIndex().invalidate(pos.getX() >> 4, pos.getZ() >> 4);
    }
    @Inject(method = "onChunkLoaded", at = @At("RETURN"))
    private void sofron$loaded(ChunkPos pos, CallbackInfo ci) {
        SofronPackLightClient.LIGHTS.chunkIndex().invalidate(pos.x(), pos.z());
    }
}
