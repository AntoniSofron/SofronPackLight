package pl.sofron.light.emissive;

import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class OreView {
    private OreView() {}

    public static void write(ByteBuffer out, Minecraft client) {
        var camera = client.gameRenderer.mainCamera();
        Vec3 eye = camera.position();
        BlockPos origin = BlockPos.containing(eye);
        Vec3 player = client.player.getPosition(camera.getCameraEntityPartialTicks(client.getDeltaTracker()));
        out.putFloat((float)(eye.x - origin.getX())).putFloat((float)(eye.y - origin.getY()))
           .putFloat((float)(eye.z - origin.getZ())).putFloat(client.level.getSkyDarken());
        out.putFloat((float)(player.x - origin.getX())).putFloat((float)(player.y - origin.getY()))
           .putFloat((float)(player.z - origin.getZ())).putFloat(0);
        BlockPos target = null;
        if (!OreMasks.current().pairs().isEmpty()) {
            // Include detached/third-person camera offset; the shader enforces the player radius.
            double reach = 33.0 + eye.distanceTo(player);
            Vec3 end = eye.add(new Vec3(camera.forwardVector()).scale(reach));
            BlockHitResult hit = client.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, camera.entity()));
            if (hit.getType() == HitResult.Type.BLOCK) target = hit.getBlockPos();
        }
        if (target == null) out.putInt(0).putInt(0).putInt(0).putInt(0);
        else out.putInt(target.getX()-origin.getX()).putInt(target.getY()-origin.getY())
                .putInt(target.getZ()-origin.getZ()).putInt(1);
    }
}
