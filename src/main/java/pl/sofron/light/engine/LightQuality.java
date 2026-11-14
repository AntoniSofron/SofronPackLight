package pl.sofron.light.engine;

/** Renderer-independent sampling presets; does not modify the Minecraft renderer. */
public enum LightQuality {
    FAST(2, 24), BALANCED(4, 48), QUALITY(8, 96);
    public final int samplesPerAxis;
    public final int maximumSources;
    LightQuality(int samplesPerAxis, int maximumSources) {
        this.samplesPerAxis = samplesPerAxis;
        this.maximumSources = maximumSources;
    }
    public static LightQuality parse(String name) {
        if (name == null) return QUALITY;
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "fast" -> FAST;
            case "balanced" -> BALANCED;
            default -> QUALITY;
        };
    }
}
