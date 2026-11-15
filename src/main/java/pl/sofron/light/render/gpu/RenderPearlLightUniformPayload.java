package pl.sofron.light.render.gpu;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Shared std140 frame parameters for the terrain vertex and fragment shaders. */
public final class RenderPearlLightUniformPayload {
    public static final int BYTE_SIZE = 64;
    private final ByteBuffer data = ByteBuffer.allocateDirect(BYTE_SIZE).order(ByteOrder.nativeOrder());

    public void update(int debugMode, int originX, int originZ, double x, double y, double z) {
        data.clear();
        data.putInt((int)Math.floor(x)).putInt((int)Math.floor(y)).putInt(debugMode).putInt((int)Math.floor(z));
        pl.sofron.light.emissive.OreView.write(data, net.minecraft.client.Minecraft.getInstance());
        data.putFloat((float)(x - originX * 16.0)).putFloat((float)y)
            .putFloat((float)(z - originZ * 16.0)).putFloat(0);
        data.flip();
    }

    public ByteBuffer readOnlyData() { return data.asReadOnlyBuffer().order(data.order()); }
}
