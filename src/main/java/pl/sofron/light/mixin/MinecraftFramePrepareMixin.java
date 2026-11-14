package pl.sofron.light.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.sofron.light.render.gpu.SodiumRenderPassCoordinator;

/** At frame entry no RenderPearl render pass has been opened yet. */
@Mixin(net.minecraft.client.renderer.GameRenderer.class)
public abstract class MinecraftFramePrepareMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void sofron$prepareLightsBeforeFrame(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        var camera = mc.gameRenderer.mainCamera();
        var pos = camera.position();
        SodiumRenderPassCoordinator.prepareFrame(pos.x, pos.y, pos.z);
    }
}
