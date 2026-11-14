package pl.sofron.light.render.gpu;

import pl.sofron.light.SofronPackLightClient;
import pl.sofron.light.render.GpuLightBuffer;

/** Uploads the tick snapshot to a real GL texture buffer at Sodium's terrain render entry.
 * Does not alter Sodium's shader pipelines: shader consumption is a separate integration.
 * Must execute on Minecraft's render thread with an active OpenGL context.
 */
public final class SodiumGpuLightBridge {
    private static LightGpuManager manager;
    private static GpuLightBuffer frame;
    private static boolean failed;
    private static long uploadCount;

    private SodiumGpuLightBridge() {}

    public static void onTerrainRender(double cameraX, double cameraY, double cameraZ) {
        if (failed || SofronPackLightClient.CONFIG == null) return;
        try {
            if (manager == null) {
                manager = new LightGpuManager(new Lwjgl33LightBackend(), 7);
                frame = new GpuLightBuffer();
            }
            frame.update(SofronPackLightClient.LIGHTS.sources(), cameraX, cameraY, cameraZ,
                    GpuLightBuffer.MAX_GPU_LIGHTS);
            manager.upload(frame);
            uploadCount++;
        } catch (RuntimeException | LinkageError exception) {
            failed = true;
            System.err.println("[Sofron Pack Light] Disabled GPU upload bridge after an error: " + exception);
            // GL resources should be released during client shutdown on render thread.
        }
    }

    /** Available to a future shader/pipeline injection on the same render thread. */
    public static LightGpuManager manager() { return manager; }

    /** For an already-active, patched terrain shader. This does not install shader patches.
     * Important: the RenderPearl pipeline may bind its program later than the terrain entry hook.
     */
    public static boolean bindActiveShader() {
        if (failed || manager == null) return false;
        try {
            return LwjglLightProgramBinder.bindCurrentProgram(manager, 0.85f);
        } catch (RuntimeException | LinkageError ex) {
            failed = true;
            System.err.println("[Sofron Pack Light] Shader binding disabled: " + ex);
            return false;
        }
    }
    public static long uploadCount() { return uploadCount; }
    public static boolean failed() { return failed; }

    public static void shutdown() {
        if (manager != null) manager.close();
        manager = null;
        frame = null;
        failed = false;
        uploadCount = 0;
    }
}
