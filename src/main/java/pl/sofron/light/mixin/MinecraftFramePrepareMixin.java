package pl.sofron.light.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.sofron.light.render.gpu.SodiumRenderPassCoordinator;

/** At frame entry no RenderPearl render pass has been opened yet. */
@Mixin(Minecraft.class)
public abstract class MinecraftFramePrepareMixin {
    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void sofron$prepareLightsBeforeFrame(boolean tick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        // Use the same camera reference as Sodium; gameRenderer's camera is the previous
        // completed camera state at frame HEAD (one-frame latency during fast motion).
        var camera = mc.gameRenderer.mainCamera();
        var pos = camera.position();
        SodiumRenderPassCoordinator.prepareFrame(pos.x, pos.y, pos.z);
    }
}
