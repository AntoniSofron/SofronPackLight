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
    private static RenderPearlChunkAtlasBuffer voxelBuffer;
    private static pl.sofron.light.runtime.DynamicVoxelField.Snapshot lastVoxels;
    private static final java.nio.ByteBuffer voxelBytes = java.nio.ByteBuffer.allocateDirect(
        16 + 4 * pl.sofron.light.runtime.DynamicVoxelField.CELLS).order(java.nio.ByteOrder.nativeOrder());
    private static long lastRevision = Long.MIN_VALUE;
    private static int lastOriginX, lastOriginZ, lastWidth;
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
        int debugMode = switch (SofronPackLightClient.CONFIG.rgbDebugMode) {
            case "raw" -> 1;
            case "strength" -> 2;
            case "mask" -> 3;
            default -> 0;
        };
        Minecraft mc = Minecraft.getInstance();
        int distance = Math.max(1, mc.options.getEffectiveRenderDistance());
        int width = 2*distance+1;
        int originX = (mc.player.blockPosition().getX() >> 4)-distance;
        int originZ = (mc.player.blockPosition().getZ() >> 4)-distance;
        payload.update(debugMode, originX, originZ, x, y, z);
        nativeBuffer.upload(payload);
        var field = SofronPackLightClient.VOXELS.field();
        if (voxelBuffer == null || field != lastVoxels) {
            if (voxelBuffer == null) voxelBuffer = new RenderPearlChunkAtlasBuffer(RenderSystem.getDevice(), "SofronVoxels");
            voxelBytes.clear();
            if (field == null) voxelBytes.putLong(0).putLong(0);
            else pl.sofron.light.runtime.DynamicVoxelField.put(voxelBytes, field);
            voxelBuffer.upload(voxelBytes.flip());
            lastVoxels = field;
        }
        var index = SofronPackLightClient.LIGHTS.chunkIndex();
        if (index.revision() != lastRevision || width != lastWidth || originX != lastOriginX || originZ != lastOriginZ) {
            try {
                ChunkLightAtlas.Snapshot atlas=ChunkLightAtlas.build(index,originX,originZ,width,0,0,0);
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
            lastRevision = index.revision();
            lastWidth = width;
            lastOriginX = originX;
            lastOriginZ = originZ;
        }
    }

    /** Only binds an already uploaded buffer; never calls CommandEncoder while a render pass is open. */
    public static void beforeDraw(RenderPass pass, CameraTransform camera) {
        if (pass == null || camera == null || nativeBuffer == null || !nativeBuffer.isReady()) return;
        nativeBuffer.bind(pass);
        if (chunkAtlasBuffer != null) chunkAtlasBuffer.bind(pass);
        oreBuffer.bind(pass);
        voxelBuffer.bind(pass);
        submissions++;
    }
    public static void close() { if (nativeBuffer != null) nativeBuffer.close(); nativeBuffer = null; if(chunkAtlasBuffer!=null) chunkAtlasBuffer.close(); chunkAtlasBuffer=null; if(oreBuffer!=null) oreBuffer.close(); oreBuffer=null; uploadedMasks=null; if(voxelBuffer!=null) voxelBuffer.close(); voxelBuffer=null; lastVoxels=null; lastRevision=Long.MIN_VALUE; }
    public static long submissions() { return submissions; }
}
