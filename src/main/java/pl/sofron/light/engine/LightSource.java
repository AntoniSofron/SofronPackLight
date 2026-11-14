package pl.sofron.light.engine;

/** Coordinates may be sub-block positions for moving entities. */
public record LightSource(double x, double y, double z, float radius, float power, RgbLight color) {
    public LightSource {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Non-finite position");
        if (!Float.isFinite(radius) || radius <= 0 || !Float.isFinite(power) || power < 0)
            throw new IllegalArgumentException("Invalid radius/power");
        if (color == null) throw new IllegalArgumentException("Null color");
    }
}
