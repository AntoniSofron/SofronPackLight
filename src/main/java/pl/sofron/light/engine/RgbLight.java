package pl.sofron.light.engine;

/** Linear RGB values in the inclusive 0..1 range. Rendering/tone mapping is a separate stage. */
public record RgbLight(float red, float green, float blue) {
    public static final RgbLight BLACK = new RgbLight(0f, 0f, 0f);

    public RgbLight {
        red = clamp(red); green = clamp(green); blue = clamp(blue);
    }
    private static float clamp(float x) { return Float.isNaN(x) ? 0f : Math.max(0f, Math.min(1f, x)); }

    public RgbLight scale(float factor) {
        return new RgbLight(red * factor, green * factor, blue * factor);
    }

    /** Additive mixing, used before display adaptation. */
    public RgbLight add(RgbLight other) {
        return new RgbLight(red + other.red, green + other.green, blue + other.blue);
    }
}
