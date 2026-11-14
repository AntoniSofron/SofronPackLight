package pl.sofron.light.render;

import java.util.List;
import pl.sofron.light.engine.LightSource;

/** A render-thread-owned frame snapshot (no access to client world on worker threads). */
public final class LightFrame {
    private final GpuLightBuffer buffer = new GpuLightBuffer();
    private long frameSerial;
    public void beginFrame(List<LightSource> sources, double cameraX, double cameraY, double cameraZ) {
        buffer.update(List.copyOf(sources), cameraX,cameraY,cameraZ,GpuLightBuffer.MAX_GPU_LIGHTS);
        frameSerial++;
    }
    public GpuLightBuffer buffer(){return buffer;}
    public long frameSerial(){return frameSerial;}
}
