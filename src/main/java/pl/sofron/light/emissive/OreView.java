package pl.sofron.light.emissive;

import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
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
           .putFloat((float)(player.z - origin.getZ())).putFloat(OreFades.current().shaderTime());
    }
}
