package pl.sofron.light;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import pl.sofron.light.render.gpu.SodiumGpuLightBridge;
import net.fabricmc.loader.api.FabricLoader;
import pl.sofron.light.config.LightConfig;
import pl.sofron.light.engine.BlockLightRules;
import pl.sofron.light.runtime.LightTracker;
import pl.sofron.light.runtime.LiveLightProbe;
import pl.sofron.light.runtime.LightDiagnostics;

/** Tracks sources and publishes the local lighting field to the Sodium terrain renderer. */
public final class SofronPackLightClient implements ClientModInitializer {
    public static final LightTracker LIGHTS = new LightTracker();
    public static final pl.sofron.light.runtime.DynamicVoxelController VOXELS = new pl.sofron.light.runtime.DynamicVoxelController();
    public static final LiveLightProbe PROBE = new LiveLightProbe();
    public static LightConfig CONFIG;
    private static LightDiagnostics diagnostics;
    @Override public void onInitializeClient() {
        diagnostics = new LightDiagnostics(FabricLoader.getInstance().getConfigDir().resolve("sofron-pack-light-diagnostics.txt"));
        CONFIG = LightConfig.load(FabricLoader.getInstance().getConfigDir().resolve("sofron-pack-light.json"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            pl.sofron.light.emissive.OreFades.tick(client);
            pl.sofron.light.runtime.LeafDetailRange.tick(client);
            LIGHTS.tick(client, CONFIG);
            VOXELS.tick(client, CONFIG);
            PROBE.tick(client, LIGHTS, CONFIG);
            diagnostics.tick(client, LIGHTS, CONFIG);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> pl.sofron.light.render.gpu.SodiumRenderPassCoordinator.close());
        System.out.println("[Sofron Pack Light] Sodium terrain lighting initialized.");
    }
}
