package pl.sofron.light.render.gpu;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import pl.sofron.light.render.GpuLightBuffer;

/** A std140-compatible uniform block payload: ivec4 header + 256 * (vec4 positionRadius, vec4 rgbPower).
 * This deliberately DOES NOT call RenderPass.setUniform: layout must be registered with RenderPearl first.
 */
public final class RenderPearlLightUniformPayload {
    public static final int HEADER_BYTES = 16;
    public static final int LIGHT_STRIDE_BYTES = 32;
    public static final int CAPACITY = GpuLightBuffer.MAX_GPU_LIGHTS;
    public static final int BYTE_SIZE = HEADER_BYTES + LIGHT_STRIDE_BYTES * CAPACITY + 48;
    private final ByteBuffer data = ByteBuffer.allocateDirect(BYTE_SIZE).order(ByteOrder.nativeOrder());
    private int count;

    public void update(GpuLightBuffer lights) { update(lights, 0, 0); }
    public void update(GpuLightBuffer lights, int movingCount) { update(lights, movingCount, 0); }

    /** Moving lights occupy [0,movingCount); stationary lights follow. */
    public void update(GpuLightBuffer lights, int movingCount, int debugMode) {
        if (lights == null) throw new IllegalArgumentException("lights");
        int n = lights.count();
        if (n < 0 || n > CAPACITY) throw new IllegalArgumentException("light count");
        data.clear();
        // std140 ivec4 alignment: count + three reserved ints.
        data.putInt(n).putInt(Math.clamp(movingCount, 0, n)).putInt(Math.clamp(debugMode, 0, 3)).putInt(0);
        FloatBuffer source = lights.readOnlyData();
        for (int i = 0; i < n * GpuLightBuffer.FLOATS_PER_LIGHT; i++) data.putFloat(source.get());
        // Clear trailing slots to prevent stale lights when shrinking the list.
        while (data.position() < BYTE_SIZE - 48) data.putLong(0L);
        pl.sofron.light.emissive.OreView.write(data, net.minecraft.client.Minecraft.getInstance());
        data.flip();
        count = n;
    }
    public int count() { return count; }
    public ByteBuffer readOnlyData() { return data.asReadOnlyBuffer().order(data.order()); }
}
