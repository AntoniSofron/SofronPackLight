package pl.sofron.light.render.gpu;

import java.nio.FloatBuffer;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;

/** Real OpenGL 3.3 implementation; no use of GL4.3 SSBOs. */
public final class Lwjgl33LightBackend implements LightGpuBackend {
    @Override public int createBuffer() { return GL15.glGenBuffers(); }
    @Override public int createTextureBuffer(int bufferId, int floatCapacity) {
        int texture = GL11Texture.gen();
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, bufferId);
        GL15.glBufferData(GL31.GL_TEXTURE_BUFFER, (long) floatCapacity * Float.BYTES, GL15.GL_DYNAMIC_DRAW);
        GL11Texture.bind(texture);
        GL31.glTexBuffer(GL31.GL_TEXTURE_BUFFER, GL30.GL_RGBA32F, bufferId);
        GL11Texture.bind(0);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
        return texture;
    }
    @Override public void upload(int bufferId, FloatBuffer packedLights) {
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, bufferId);
        GL15.glBufferSubData(GL31.GL_TEXTURE_BUFFER, 0L, packedLights);
        GL15.glBindBuffer(GL31.GL_TEXTURE_BUFFER, 0);
    }
    @Override public void bind(int textureId, int textureUnit) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + textureUnit);
        GL11Texture.bind(textureId);
    }
    @Override public void deleteTexture(int textureId) { GL11Texture.delete(textureId); }
    @Override public void deleteBuffer(int bufferId) { GL15.glDeleteBuffers(bufferId); }
    private static final class GL11Texture {
        static int gen() { return org.lwjgl.opengl.GL11.glGenTextures(); }
        static void bind(int id) { org.lwjgl.opengl.GL11.glBindTexture(GL31.GL_TEXTURE_BUFFER, id); }
        static void delete(int id) { org.lwjgl.opengl.GL11.glDeleteTextures(id); }
    }
}
