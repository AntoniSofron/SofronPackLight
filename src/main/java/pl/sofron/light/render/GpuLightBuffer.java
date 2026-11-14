package pl.sofron.light.render;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;
import pl.sofron.light.engine.LightSource;

/** Renderer-neutral per-frame light buffer. Interleaved xyz/radius + linear RGB/power.
 * Stable selection prevents changes in entity iteration order from causing flicker.
 * The camera-relative coordinates preserve precision at large world coordinates.
 */
public final class GpuLightBuffer {
    public static final int FLOATS_PER_LIGHT = 8;
    public static final int MAX_GPU_LIGHTS = 256;
    private final FloatBuffer data = ByteBuffer.allocateDirect(MAX_GPU_LIGHTS * FLOATS_PER_LIGHT * Float.BYTES)
        .order(ByteOrder.nativeOrder()).asFloatBuffer();
    private int count;
    public void update(List<LightSource> sources, double cx, double cy, double cz, int limit) {
        if (sources == null) throw new IllegalArgumentException("sources");
        int n = Math.clamp(limit, 0, MAX_GPU_LIGHTS);
        data.clear();
        // LightTracker already selects and orders a stable subset. A second sort
        // around the interpolated camera caused the 96th light to swap between
        // frames as the camera moved even when the client tick snapshot was fixed.
        var selected = sources.stream()
            .filter(s -> Double.isFinite(s.x()) && Double.isFinite(s.y()) && Double.isFinite(s.z()))
            .limit(n).toList();
        for (var s : selected) {
            data.put((float)(s.x()-cx)).put((float)(s.y()-cy)).put((float)(s.z()-cz)).put(s.radius());
            data.put(s.color().red()).put(s.color().green()).put(s.color().blue()).put(s.power());
        }
        count=selected.size(); data.flip();
    }
    private static double distanceSquared(LightSource s,double x,double y,double z) {
        double dx=s.x()-x,dy=s.y()-y,dz=s.z()-z;return dx*dx+dy*dy+dz*dz;
    }
    public int count(){return count;}
    public FloatBuffer readOnlyData(){return data.asReadOnlyBuffer();}
}
