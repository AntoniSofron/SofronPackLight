package pl.sofron.light.render.gpu;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import java.nio.ByteBuffer;
import pl.sofron.light.runtime.ChunkLightAtlas;

/** One immutable-per-upload R32_SINT atlas used by every terrain draw in a frame.
 * All GPU commands take place at Minecraft.renderFrame HEAD (outside render pass).
 */
public final class RenderPearlChunkAtlasBuffer implements AutoCloseable {
    private final GpuDevice device;
    private GpuBuffer buffer;
    private int capacityBytes;
    private boolean ready;
    public RenderPearlChunkAtlasBuffer(GpuDevice device) { this.device=device; }
    public void upload(ChunkLightAtlas.Snapshot atlas) {
        ByteBuffer data=atlas.bytes().asReadOnlyBuffer();
        int size=data.remaining();
        if (buffer == null || size > capacityBytes) {
            if (buffer != null) buffer.close();
            capacityBytes=size;
            buffer=device.createBuffer(() -> "Sofron RGB chunk-indexed atlas",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, capacityBytes);
        }
        CommandEncoder encoder=device.createCommandEncoder();
        encoder.writeToBuffer(buffer.slice(), data);
        encoder.submit();
        ready=true;
    }
    public void bind(RenderPass pass) {
        if (ready && buffer != null) pass.setUniform("SofronLightAtlas", buffer.slice());
    }
    @Override public void close() {
        ready=false;
        if(buffer!=null) buffer.close();
        buffer=null;
    }
}
