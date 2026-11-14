package pl.sofron.light.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import pl.sofron.light.engine.LightSource;

/** CPU-side immutable batches for a destination chunk. No global light count cap.
 * A batch limit applies only to one GPU page, never to the indexed world.
 * This does not yet install per-batch GPU binding into Sodium.
 */
public final class ChunkLocalLightPlanner {
    public static final int PAGE_CAPACITY = 256;
    private ChunkLocalLightPlanner() {}

    public record Page(int chunkX, int chunkZ, int pageIndex, List<LightSource> lights) {
        public Page { lights = List.copyOf(lights); }
    }

    /** Converts all sources overlapping a chunk into deterministic fixed-size pages.
     * Reproducible ordering reduces flicker caused by hash-map iteration order.
     */
    public static List<Page> pages(ChunkLightBuckets index, int chunkX, int chunkZ,
                                   List<LightSource> moving) {
        List<LightSource> relevant = new ArrayList<>(index.affecting(chunkX, chunkZ));
        if (moving != null) {
            for (LightSource light : moving) {
                double minX = chunkX * 16.0, maxX = minX + 16.0;
                double minZ = chunkZ * 16.0, maxZ = minZ + 16.0;
                double dx = light.x() - Math.clamp(light.x(), minX, maxX);
                double dz = light.z() - Math.clamp(light.z(), minZ, maxZ);
                if (dx * dx + dz * dz <= light.radius() * light.radius()) relevant.add(light);
            }
        }
        relevant.sort(Comparator.comparingDouble(LightSource::x)
            .thenComparingDouble(LightSource::z).thenComparingDouble(LightSource::y)
            .thenComparingDouble(LightSource::radius)
            .thenComparingDouble(LightSource::power));
        List<Page> pages = new ArrayList<>();
        for (int offset = 0; offset < relevant.size(); offset += PAGE_CAPACITY) {
            pages.add(new Page(chunkX, chunkZ, pages.size(),
                relevant.subList(offset, Math.min(offset + PAGE_CAPACITY, relevant.size()))));
        }
        return List.copyOf(pages);
    }
}
