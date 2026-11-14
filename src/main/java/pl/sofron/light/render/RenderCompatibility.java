package pl.sofron.light.render;

/** The choice of GPU backend must remain explicit: Iris shaderpacks can replace
 * Sodium render programs. Compatibility cannot be inferred from installed mods.
 */
public final class RenderCompatibility {
    public enum Backend { VANILLA, SODIUM, IRIS_SHADERPACK, UNKNOWN }
    public record Environment(boolean sodiumLoaded, boolean irisLoaded, boolean irisShaderpackActive) {}
    public static Backend choose(Environment env) {
        if (env.irisShaderpackActive()) return Backend.IRIS_SHADERPACK;
        if (env.sodiumLoaded()) return Backend.SODIUM;
        if (!env.irisLoaded()) return Backend.VANILLA;
        return Backend.UNKNOWN;
    }
    private RenderCompatibility() {}
}
