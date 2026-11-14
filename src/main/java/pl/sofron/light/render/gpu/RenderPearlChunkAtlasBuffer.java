package pl.sofron.light.render.gpu;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import java.nio.ByteBuffer;
import pl.sofron.light.runtime.ChunkLightAtlas;

/** One immutable-per-upload R32_SINT atlas used by every terrain draw in a frame.
 * All GPU commands take place at GameRenderer.renderLevel HEAD (outside render pass).
 */
public final class RenderPearlChunkAtlasBuffer implements AutoCloseable {
    private final GpuDevice device;
    private final String uniform;
    private GpuBuffer buffer;
    private int capacityBytes;
    private boolean ready;
    public RenderPearlChunkAtlasBuffer(GpuDevice device, String uniform) { this.device=device; this.uniform=uniform; }
    public void upload(ChunkLightAtlas.Snapshot atlas) {
        upload(atlas.bytes().asReadOnlyBuffer());
    }
    public void upload(ByteBuffer data) {
        int size=data.remaining();
        if (buffer == null || size > capacityBytes) {
            if (buffer != null) buffer.close();
            capacityBytes=size;
            buffer=device.createBuffer(() -> uniform,
                GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER | GpuBuffer.USAGE_COPY_DST, capacityBytes);
        }
        CommandEncoder encoder=device.createCommandEncoder();
        encoder.writeToBuffer(buffer.slice(0, size), data);
        encoder.submit();
        ready=true;
    }
    public void bind(RenderPass pass) {
        if (ready && buffer != null) pass.setUniform(uniform, buffer);
    }
    @Override public void close() {
        ready=false;
        if(buffer!=null) buffer.close();
        buffer=null;
    }
}
