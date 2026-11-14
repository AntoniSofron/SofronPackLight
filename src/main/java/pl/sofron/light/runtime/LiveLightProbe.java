package pl.sofron.light.runtime;

import net.minecraft.client.Minecraft;
import pl.sofron.light.config.LightConfig;
import pl.sofron.light.engine.RgbLight;
import pl.sofron.light.engine.RgbLightField;

/**
 * Native-world sampling bridge. This is NOT a surface renderer: consumers can
 * query RGB at any world-space position without altering chunks or block light.
 */
public final class LiveLightProbe {
    private volatile RgbLight eyeLight = RgbLight.BLACK;
    public RgbLight eyeLight() { return eyeLight; }

    public RgbLight sample(Minecraft client, LightTracker tracker, LightConfig cfg,
                           double x, double y, double z) {
        if (client.level == null || !cfg.dynamicLighting) return RgbLight.BLACK;
        var nearby = new java.util.ArrayList<>(tracker.sources());
        nearby.addAll(tracker.chunkIndex().lightsAffectingChunk((int)Math.floor(x) >> 4, (int)Math.floor(z) >> 4));
        return RgbLightField.sample(x, y, z, nearby,
            cfg.intensityPercent / 100.0f, new MinecraftOcclusion(client.level));
    }

    public void tick(Minecraft client, LightTracker tracker, LightConfig cfg) {
        if (client.player == null || client.level == null) {
            eyeLight = RgbLight.BLACK;
            return;
        }
        eyeLight = sample(client, tracker, cfg, client.player.getX(),
            client.player.getEyeY(), client.player.getZ());
    }
}
