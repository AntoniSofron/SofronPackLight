package pl.sofron.light.engine;

/** World adapter: return true only for cells that fully block light. */
@FunctionalInterface
public interface LightOcclusion {
    boolean blocks(int x, int y, int z);
    LightOcclusion NONE = (x, y, z) -> false;
}
