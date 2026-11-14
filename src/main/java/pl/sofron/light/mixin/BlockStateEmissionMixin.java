package pl.sofron.light.mixin;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.sofron.light.engine.BlockLightRules;

/** Overriding block emission must happen in vanilla lighting, not in a post-process shader. */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateEmissionMixin {
    @Inject(method = "getLightEmission", at = @At("HEAD"), cancellable = true)
    private void sofron$vanillaEmission(CallbackInfoReturnable<Integer> cir) {
        BlockBehaviour.BlockStateBase self = (BlockBehaviour.BlockStateBase) (Object) this;
        Integer value = BlockLightRules.vanillaEmissionOverride(
            BuiltInRegistries.BLOCK.getKey(self.getBlock()).toString());
        if (value != null) cir.setReturnValue(value);
    }
}
