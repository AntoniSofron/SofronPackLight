package pl.sofron.light.render.gpu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import pl.sofron.light.SofronPackLightClient;
import pl.sofron.light.render.GpuLightBuffer;
import pl.sofron.light.runtime.ChunkLightAtlas;
import net.minecraft.client.Minecraft;
import pl.sofron.light.emissive.OreMasks;

/** Per-frame data upload followed by RenderPass binding. Render-thread only. */
public final class SodiumRenderPassCoordinator {
    private static RenderPearlNativeLightBuffer nativeBuffer;
    private static RenderPearlChunkAtlasBuffer chunkAtlasBuffer;
    private static RenderPearlChunkAtlasBuffer oreBuffer;
    private static OreMasks uploadedMasks;
    private static ChunkLightAtlas.Snapshot lastAtlas;
    private static long lastRevision = Long.MIN_VALUE;
    private static int lastOriginX = Integer.MIN_VALUE, lastOriginZ = Integer.MIN_VALUE, lastWidth;
    private static final GpuLightBuffer frame = new GpuLightBuffer();
    private static final RenderPearlLightUniformPayload payload = new RenderPearlLightUniformPayload();
    private static long submissions;
    private static volatile int diagnosticAtlasReferences;
    private static volatile int diagnosticAtlasCells;
    private static volatile long diagnosticAtlasFailures;
    public static int diagnosticAtlasReferences() { return diagnosticAtlasReferences; }
    public static int diagnosticAtlasCells() { return diagnosticAtlasCells; }
    public static long diagnosticAtlasFailures() { return diagnosticAtlasFailures; }
    public static long diagnosticDrawSubmissions() { return submissions; }
    private SodiumRenderPassCoordinator() {}

    /** Upload only before the frame graph opens its render passes. Called from GameRenderer.renderLevel HEAD. */
    public static void prepareFrame(double x, double y, double z) {
        if (SofronPackLightClient.CONFIG == null) return;
        if (nativeBuffer == null) nativeBuffer = new RenderPearlNativeLightBuffer(RenderSystem.getDevice());
        if (chunkAtlasBuffer == null) chunkAtlasBuffer = new RenderPearlChunkAtlasBuffer(RenderSystem.getDevice(), "SofronLightAtlas");
        if (oreBuffer == null) oreBuffer = new RenderPearlChunkAtlasBuffer(RenderSystem.getDevice(), "SofronOreRects");
        var masks = OreMasks.current();
        if (uploadedMasks != masks) {
            oreBuffer.upload(masks.rectangles().duplicate());
            uploadedMasks = masks;
        }
        // Keep the original dynamic-item illumination, but no longer put placed lights
        // in the global 256-entry uniform array.
        int moving = SofronPackLightClient.LIGHTS.movingCount();
        var current = SofronPackLightClient.LIGHTS.sources();
        frame.update(current.subList(0, Math.min(moving, current.size())), x, y, z, GpuLightBuffer.MAX_GPU_LIGHTS);
        int debugMode = switch (SofronPackLightClient.CONFIG.rgbDebugMode) {
            case "raw" -> 1;
            case "strength" -> 2;
            case "mask" -> 3;
            default -> 0;
        };
        payload.update(frame, frame.count(), debugMode);
        nativeBuffer.upload(payload);
        Minecraft mc=Minecraft.getInstance();
        if (mc.player != null && mc.level != null) {
            var index=SofronPackLightClient.LIGHTS.chunkIndex();
            int distance=Math.max(1, mc.options.renderDistance().get());
            int width=2*distance+1;
            int originX=(mc.player.blockPosition().getX() >> 4)-distance;
            int originZ=(mc.player.blockPosition().getZ() >> 4)-distance;
            // Camera position is encoded inside the atlas, therefore it is rebuilt
            // each frame. A future optimisation should separate static and camera data.
            try {
                ChunkLightAtlas.Snapshot atlas=ChunkLightAtlas.build(index,originX,originZ,width,x,y,z, SofronPackLightClient.VOXELS.field());
                chunkAtlasBuffer.upload(atlas);
                diagnosticAtlasReferences = atlas.lightReferences();
                diagnosticAtlasCells = atlas.chunkCount();
            } catch (IllegalStateException | IllegalArgumentException atlasLimit) {
                diagnosticAtlasFailures++;
                diagnosticAtlasReferences = 0;
                diagnosticAtlasCells = 1;
                // A very dense world must not crash the renderer. Disable this
                // frame's stationary RGB rather than reuse a stale atlas.
                chunkAtlasBuffer.upload(ChunkLightAtlas.empty(x,y,z));
            }
        } else {
            chunkAtlasBuffer.upload(ChunkLightAtlas.empty(x,y,z));
            diagnosticAtlasReferences = 0;
            diagnosticAtlasCells = 1;
        }
    }

    /** Only binds an already uploaded buffer; never calls CommandEncoder while a render pass is open. */
    public static void beforeDraw(RenderPass pass, CameraTransform camera) {
        if (pass == null || camera == null || nativeBuffer == null || !nativeBuffer.isReady()) return;
        nativeBuffer.bind(pass);
        if (chunkAtlasBuffer != null) chunkAtlasBuffer.bind(pass);
        oreBuffer.bind(pass);
        submissions++;
    }
    public static void close() { if (nativeBuffer != null) nativeBuffer.close(); nativeBuffer = null; if(chunkAtlasBuffer!=null) chunkAtlasBuffer.close(); chunkAtlasBuffer=null; if(oreBuffer!=null) oreBuffer.close(); oreBuffer=null; uploadedMasks=null; }
    public static long submissions() { return submissions; }
}
