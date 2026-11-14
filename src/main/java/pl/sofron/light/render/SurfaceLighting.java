package pl.sofron.light.render;

import java.util.List;
import pl.sofron.light.engine.LightOcclusion;
import pl.sofron.light.engine.LightSource;
import pl.sofron.light.engine.RgbLight;
import pl.sofron.light.engine.RgbLightField;

/**
 * Renderer-independent vertex/fragment lighting contract. The output is a linear RGB
 * light contribution, not a block light level. Consumers must apply it in their
 * terrain/entity fragment shader, with correct gamma and light mapping.
 *
 * This DOES NOT hook itself into Minecraft, Sodium, or Iris.
 */
public final class SurfaceLighting {
    private SurfaceLighting() {}

    public static RgbLight atWorldPosition(double x, double y, double z,
            List<LightSource> sources, LightOcclusion occlusion, float intensity) {
        return RgbLightField.sample(x, y, z, sources, intensity, occlusion);
    }

    /** Linear-space additive illumination, combined with a baseline light level. */
    public static RgbLight litAlbedo(RgbLight albedo, RgbLight baseline,
                                     RgbLight dynamic, float maxExposure) {
        if (albedo == null || baseline == null || dynamic == null) throw new IllegalArgumentException("null color");
        float cap = Math.max(0f, Math.min(8f, maxExposure));
        return new RgbLight(
            albedo.red() * Math.min(cap, baseline.red() + dynamic.red()),
            albedo.green() * Math.min(cap, baseline.green() + dynamic.green()),
            albedo.blue() * Math.min(cap, baseline.blue() + dynamic.blue()));
    }
}
