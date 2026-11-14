package pl.sofron.light.core;

import java.util.List;
import java.util.Objects;
import pl.sofron.light.engine.LightOcclusion;
import pl.sofron.light.engine.LightSource;
import pl.sofron.light.engine.RgbLight;
import pl.sofron.light.engine.RgbLightField;

/**
 * Sofron-owned CPU lighting core. Does not contain classes or archives from other mods.
 * GPU upload and world surface rendering must be supplied by a separate renderer backend.
 */
public final class NativeLightCore {
    private volatile List<LightSource> snapshot = List.of();
    private volatile float intensity = 0.85f;
    private volatile LightOcclusion occlusion = LightOcclusion.NONE;

    public void update(List<LightSource> sources, float intensity, LightOcclusion occlusion) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(occlusion, "occlusion");
        if (!Float.isFinite(intensity) || intensity < 0f || intensity > 4f)
            throw new IllegalArgumentException("Intensity must be between 0 and 4");
        this.snapshot = List.copyOf(sources);
        this.intensity = intensity;
        this.occlusion = occlusion;
    }

    public RgbLight evaluate(double x, double y, double z) {
        return RgbLightField.sample(x, y, z, snapshot, intensity, occlusion);
    }

    public int sourceCount() { return snapshot.size(); }
    public void clear() { snapshot = List.of(); }
}
