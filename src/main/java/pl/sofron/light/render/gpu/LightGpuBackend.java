package pl.sofron.light.render.gpu;

import java.nio.FloatBuffer;

/** All methods execute on Minecraft's render thread with an active OpenGL context. */
public interface LightGpuBackend {
    int createBuffer();
    int createTextureBuffer(int bufferId, int floatCapacity);
    void upload(int bufferId, FloatBuffer packedLights);
    void bind(int textureId, int textureUnit);
    void deleteTexture(int textureId);
    void deleteBuffer(int bufferId);
}
