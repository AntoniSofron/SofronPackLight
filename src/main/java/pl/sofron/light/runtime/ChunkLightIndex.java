package pl.sofron.light.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import pl.sofron.light.config.LightConfig;
import pl.sofron.light.engine.LightPalette;
import pl.sofron.light.engine.BlockLightRules;
import pl.sofron.light.engine.LightSource;
import pl.sofron.light.engine.RgbLight;

/**
 * Persistent client-side chunk index, independent of the 256-element GPU uniform.
 * Scans a small bounded number of loaded chunks per tick; never loads chunks.
 * Removed/changed indexed light sources are validated every client tick.
 * This is an indexing foundation, NOT yet per-chunk GPU rendering.
 */
public final class ChunkLightIndex {
    private record ChunkKey(int x, int z) {}
    private record Entry(List<LightSource> lights, long scannedTick) {}
    private final Map<ChunkKey, Entry> indexed = new HashMap<>();
    private final ChunkLightBuckets buckets = new ChunkLightBuckets();
    private Object lastLevel;
    private long ticks;
    private long revision;
    private long lastRuleRevision = Long.MIN_VALUE;
    public long revision() { return revision; }
    private static final int SCANS_PER_TICK = 3;
    private static final int REFRESH_TICKS = 600;
    private final Set<ChunkKey> dirty = new HashSet<>();
    public void invalidate(int cx, int cz) { dirty.add(new ChunkKey(cx,cz)); }
    public void clear() {
        if (lastLevel != null || !indexed.isEmpty()) revision++;
        indexed.clear(); buckets.clear(); dirty.clear(); lastLevel = null; ticks = 0;
    }
    public int indexedChunks() { return indexed.size(); }
    public int indexedLights() { return indexed.values().stream().mapToInt(e -> e.lights().size()).sum(); }

    public void tick(Minecraft client, LightConfig cfg) {
        if (client.level == null || client.player == null) { clear(); return; }
        if (lastLevel != client.level) { indexed.clear(); buckets.clear(); lastLevel = client.level; ticks = 0; revision++; }
        ticks++;
        long currentRuleRevision = BlockLightRules.revision();
        if (lastRuleRevision != currentRuleRevision) {
            indexed.clear(); buckets.clear(); revision++;
            lastRuleRevision = currentRuleRevision;
        }
        int cx = client.player.blockPosition().getX() >> 4;
        int cz = client.player.blockPosition().getZ() >> 4;
        int radius = Math.max(1, client.options.renderDistance().get());
        // Remove stale entries outside view distance, including previously visited regions.
        indexed.keySet().removeIf(k -> {
            boolean stale = Math.abs(k.x - cx) > radius + 1 || Math.abs(k.z - cz) > radius + 1
                || !client.level.hasChunk(k.x, k.z);
            if (stale) { buckets.remove(k.x, k.z); revision++; }
            return stale;
        });
        dirty.retainAll(indexed.keySet());
        List<ChunkKey> candidates = new ArrayList<>();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int x = cx + dx, z = cz + dz;
                if (!client.level.hasChunk(x,z)) continue;
                ChunkKey key = new ChunkKey(x,z);
                Entry old = indexed.get(key);
                if (old == null || dirty.contains(key) || ticks - old.scannedTick >= REFRESH_TICKS) candidates.add(key);
            }
        }
        // Unscanned chunks always go first, otherwise nearby refreshes can starve
        // distant chunks indefinitely. Refresh the oldest indexed chunks after that.
        candidates.sort(Comparator
            .comparingInt((ChunkKey k) -> dirty.contains(k) ? -1 : indexed.containsKey(k) ? 1 : 0)
            .thenComparingLong(k -> indexed.containsKey(k) ? indexed.get(k).scannedTick : -1L)
            .thenComparingInt(k -> Math.max(Math.abs(k.x-cx), Math.abs(k.z-cz))));
        int scans = Math.min(SCANS_PER_TICK, candidates.size());
        for (int i = 0; i < scans; i++) {
            ChunkKey key = candidates.get(i);
            List<LightSource> lights = scanChunk(client, cfg, key.x, key.z);
            Entry old = indexed.put(key, new Entry(lights, ticks));
            dirty.remove(key);
            if (old == null ? !lights.isEmpty() : !old.lights().equals(lights)) {
                buckets.replace(key.x, key.z, lights);
                revision++;
            }
        }
    }

    /** All indexed stationary sources, without imposing a GPU limit. */
    public List<LightSource> allLights() { return buckets.allLights(); }

    /** CPU-side selection query, no GPU truncation. */
    public List<LightSource> lightsAffectingChunk(int chunkX, int chunkZ) {
        return buckets.affecting(chunkX, chunkZ);
    }

    private static List<LightSource> scanChunk(Minecraft client, LightConfig cfg, int cx, int cz) {
        List<LightSource> found = new ArrayList<>();
        var chunk = client.level.getChunkSource().getChunkNow(cx, cz);
        if (chunk == null || !cfg.coloredLighting) return List.of();
        for (int sectionIndex = 0; sectionIndex < chunk.getSections().length; sectionIndex++) {
            var section = chunk.getSection(sectionIndex);
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.getLightEmission() > 0)) continue;
            int baseY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
            for (int dy=0; dy<16; dy++) for (int dx=0; dx<16; dx++) for (int dz=0; dz<16; dz++) {
                var state = section.getBlockState(dx, dy, dz);
                if (state.getLightEmission() == 0) continue;
                int x = (cx << 4) + dx, y = baseY + dy, z = (cz << 4) + dz;
                String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                var rule = BlockLightRules.rule(id);
                // No rule means vanilla neutral light: no recoloring is necessary.
                if (rule == null || !cfg.coloredLighting) continue;
                int vanilla = state.getLightEmission(); // actual override now supplied by BlockStateEmissionMixin
                int visual = rule.emission() == null ? vanilla : rule.emission();
                if (visual <= 0 || rule.intensity() <= 0) continue;
                float radius = BlockLightRules.tintRadius(rule, (float)cfg.dynamicLightRadius, visual);
                if (radius <= 0) continue;
                found.add(new LightSource(x+.5, y+.5, z+.5,
                    radius, rule.intensity(), rule.color()));
            }
        }
        return List.copyOf(found);
    }
}
