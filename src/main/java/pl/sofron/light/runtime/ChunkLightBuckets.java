package pl.sofron.light.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pl.sofron.light.engine.LightSource;

/** Pure spatial look-up for stationary lights; one bucket per source chunk.
 * Client-tick owned (not thread safe). A query only examines buckets whose
 * bounds can intersect the destination chunk plus maximum light reach.
 */
public final class ChunkLightBuckets {
    private final Map<Long, List<LightSource>> buckets = new HashMap<>();
    private float maxRadius;

    public void clear() { buckets.clear(); maxRadius = 0f; }
    public void replace(int cx, int cz, List<LightSource> sources) {
        long key = key(cx, cz);
        if (sources.isEmpty()) buckets.remove(key);
        else buckets.put(key, List.copyOf(sources));
        recomputeMaxRadius();
    }
    public void remove(int cx, int cz) { buckets.remove(key(cx, cz)); recomputeMaxRadius(); }
    public int chunkCount() { return buckets.size(); }
    public int sourceCount() { return buckets.values().stream().mapToInt(List::size).sum(); }
    public List<LightSource> allLights() {
        ArrayList<LightSource> result = new ArrayList<>();
        buckets.values().forEach(result::addAll);
        return result;
    }
    public List<LightSource> affecting(int cx, int cz) {
        ArrayList<LightSource> found = new ArrayList<>();
        int reach = (int)Math.ceil(maxRadius / 16.0);
        double minX = cx * 16.0, maxX = minX + 16.0;
        double minZ = cz * 16.0, maxZ = minZ + 16.0;
        for (int x = cx-reach; x <= cx+reach; x++) {
            for (int z = cz-reach; z <= cz+reach; z++) {
                List<LightSource> entries = buckets.get(key(x,z));
                if (entries == null) continue;
                for (LightSource light : entries) {
                    double nearX = Math.max(minX, Math.min(maxX, light.x()));
                    double nearZ = Math.max(minZ, Math.min(maxZ, light.z()));
                    double dx = nearX - light.x(), dz = nearZ - light.z();
                    if (dx * dx + dz * dz <= light.radius() * light.radius()) found.add(light);
                }
            }
        }
        return found;
    }
    private void recomputeMaxRadius() {
        maxRadius = 0f;
        for (List<LightSource> sources : buckets.values())
            for (LightSource light : sources) maxRadius = Math.max(maxRadius, light.radius());
    }
    private static long key(int x, int z) {
        return ((long)x << 32) | (z & 0xffffffffL);
    }
}
