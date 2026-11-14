package pl.sofron.light.render.gpu;

import java.nio.FloatBuffer;
import pl.sofron.light.render.GpuLightBuffer;

/** One OpenGL buffer/texture pair per renderer. Must only be used on the render thread. */
public final class LightGpuManager implements AutoCloseable {
    public static final int FLOAT_CAPACITY = GpuLightBuffer.MAX_GPU_LIGHTS * GpuLightBuffer.FLOATS_PER_LIGHT;
    private final LightGpuBackend backend;
    private final int textureUnit;
    private int bufferId;
    private int textureId;
    private int count;
    private boolean closed;

    public LightGpuManager(LightGpuBackend backend, int textureUnit) {
        if (backend == null) throw new IllegalArgumentException("backend");
        if (textureUnit < 0) throw new IllegalArgumentException("textureUnit");
        this.backend = backend;
        this.textureUnit = textureUnit;
    }

    public void upload(GpuLightBuffer lightData) {
        if (closed) throw new IllegalStateException("GPU manager closed");
        if (lightData == null) throw new IllegalArgumentException("lightData");
        if (bufferId == 0) {
            bufferId = backend.createBuffer();
            if (bufferId == 0) throw new IllegalStateException("Cannot create GL buffer");
            textureId = backend.createTextureBuffer(bufferId, FLOAT_CAPACITY);
            if (textureId == 0) { backend.deleteBuffer(bufferId); bufferId = 0; throw new IllegalStateException("Cannot create texture buffer"); }
        }
        FloatBuffer data = lightData.readOnlyData();
        backend.upload(bufferId, data);
        count = lightData.count();
    }

    /** Call directly before terrain shader draw. Set uniform splLightData to textureUnit. */
    public void bind() {
        if (closed || textureId == 0) throw new IllegalStateException("GPU buffer not initialized");
        backend.bind(textureId, textureUnit);
    }
    public int count() { return count; }
    public int textureUnit() { return textureUnit; }
    @Override public void close() {
        if (closed) return;
        closed = true;
        if (textureId != 0) backend.deleteTexture(textureId);
        if (bufferId != 0) backend.deleteBuffer(bufferId);
        textureId=0; bufferId=0; count=0;
    }
}
