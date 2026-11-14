package pl.sofron.light.emissive;

import java.util.Optional;

/** OptiFine-style _e texture naming, independent from GPU upload or resource IO. */
public final class EmissiveMaskPaths {
    private EmissiveMaskPaths() {}
    public static Optional<String> baseForMask(String path, String suffix) {
        if (path == null || suffix == null || suffix.isBlank() || !path.endsWith(".png")) return Optional.empty();
        String stem = path.substring(0, path.length() - 4);
        if (!stem.endsWith(suffix) || stem.length() <= suffix.length()) return Optional.empty();
        return Optional.of(stem.substring(0, stem.length() - suffix.length()) + ".png");
    }
    public static Optional<String> maskForBase(String path, String suffix) {
        if (path == null || suffix == null || suffix.isBlank() || !path.endsWith(".png")) return Optional.empty();
        return Optional.of(path.substring(0, path.length() - 4) + suffix + ".png");
    }
}
