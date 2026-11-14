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
    // Validation visits only currently indexed light positions, not every block
    // in thousands of visible chunks. Removing a light updates the GPU atlas
    // on the next render preparation because revision / bucket data change.
    private static final int VALIDATION_PERIOD_TICKS = 1;
    // Check a small local volume on every tick. Full chunk rescans are too slow
    // to discover newly placed lights at large render distances.
    private static final int LOCAL_HORIZONTAL_RADIUS = 9;
    private static final int LOCAL_VERTICAL_RADIUS = 7;

    public void clear() { indexed.clear(); buckets.clear(); lastLevel = null; ticks = 0; revision++; }
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
        if (ticks % VALIDATION_PERIOD_TICKS == 0) validateExistingSources(client, cfg);
        discoverNewNearbySources(client, cfg);
        List<ChunkKey> candidates = new ArrayList<>();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                int x = cx + dx, z = cz + dz;
                if (!client.level.hasChunk(x,z)) continue;
                ChunkKey key = new ChunkKey(x,z);
                Entry old = indexed.get(key);
                if (old == null || ticks - old.scannedTick >= REFRESH_TICKS) candidates.add(key);
            }
        }
        // Unscanned chunks always go first, otherwise nearby refreshes can starve
        // distant chunks indefinitely. Refresh the oldest indexed chunks after that.
        candidates.sort(Comparator
            .comparingInt((ChunkKey k) -> indexed.containsKey(k) ? 1 : 0)
            .thenComparingLong(k -> indexed.containsKey(k) ? indexed.get(k).scannedTick : -1L)
            .thenComparingInt(k -> Math.max(Math.abs(k.x-cx), Math.abs(k.z-cz))));
        int scans = Math.min(SCANS_PER_TICK, candidates.size());
        for (int i = 0; i < scans; i++) {
            ChunkKey key = candidates.get(i);
            List<LightSource> lights = scanChunk(client, cfg, key.x, key.z);
            indexed.put(key, new Entry(lights, ticks));
            buckets.replace(key.x, key.z, lights);
            revision++;
        }
    }

    /** Immediately retire invalid/removed or switched-off stationary sources.
     * This does not require a chunk rebuild and works even when the normal
     * refresh queue is starved by a very large render distance.
     */
    private void validateExistingSources(Minecraft client, LightConfig cfg) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // Snapshot iteration prevents accidental mutation while replacing entries.
        for (Map.Entry<ChunkKey, Entry> slot : indexed.entrySet()) {
            List<LightSource> old = slot.getValue().lights();
            if (old.isEmpty()) continue;
            List<LightSource> retained = null;
            for (int i = 0; i < old.size(); i++) {
                LightSource light = old.get(i);
                int x = (int)Math.floor(light.x());
                int y = (int)Math.floor(light.y());
                int z = (int)Math.floor(light.z());
                pos.set(x,y,z);
                var state = client.level.getBlockState(pos);
                String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                var rule = BlockLightRules.rule(id);
                int emission = rule == null ? 0 : (rule.emission() == null
                    ? state.getLightEmission() : rule.emission());
                float radius = BlockLightRules.tintRadius(rule, (float)cfg.dynamicLightRadius, emission);
                boolean valid = rule != null && cfg.coloredLighting && emission > 0
                    && rule.intensity() > 0
                    && Math.abs(light.radius() - radius) < 0.001f
                    && Math.abs(light.power() - rule.intensity()) < 0.001f
                    && light.color().equals(rule.color());
                if (!valid && retained == null) retained = new ArrayList<>(old.subList(0, i));
                else if (valid && retained != null) retained.add(light);
            }
            if (retained != null) {
                List<LightSource> result = List.copyOf(retained);
                slot.setValue(new Entry(result, slot.getValue().scannedTick()));
                buckets.replace(slot.getKey().x, slot.getKey().z, result);
                revision++;
            }
        }
    }

    /**
     * Discover newly placed sources close to the player independently of the
     * background full-chunk scan queue. We never load a chunk here.
     * Existing positions are deduplicated and everything is submitted through
     * the same bucket/revision path used by the atlas uploader.
     */
    private void discoverNewNearbySources(Minecraft client, LightConfig cfg) {
        if (!cfg.coloredLighting || !cfg.stationaryBlockLighting) return;
        BlockPos center = client.player.blockPosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Map<ChunkKey, List<LightSource>> additions = new HashMap<>();
        Map<ChunkKey, Set<Long>> knownPositions = new HashMap<>();
        for (int dx = -LOCAL_HORIZONTAL_RADIUS; dx <= LOCAL_HORIZONTAL_RADIUS; dx++) {
            int x = center.getX() + dx;
            for (int dz = -LOCAL_HORIZONTAL_RADIUS; dz <= LOCAL_HORIZONTAL_RADIUS; dz++) {
                int z = center.getZ() + dz;
                ChunkKey key = new ChunkKey(x >> 4, z >> 4);
                if (!client.level.hasChunk(key.x, key.z)) continue;
                Entry entry = indexed.get(key);
                // Background indexing discovers lights in previously unscanned chunks.
                if (entry == null) continue;
                Set<Long> positions = knownPositions.computeIfAbsent(key, k -> {
                    Set<Long> set = new HashSet<>();
                    for (LightSource light : entry.lights()) {
                        set.add(BlockPos.asLong((int)Math.floor(light.x()),
                            (int)Math.floor(light.y()), (int)Math.floor(light.z())));
                    }
                    return set;
                });
                for (int dy = -LOCAL_VERTICAL_RADIUS; dy <= LOCAL_VERTICAL_RADIUS; dy++) {
                    int y = center.getY() + dy;
                    pos.set(x, y, z);
                    if (positions.contains(pos.asLong())) continue;
                    var state = client.level.getBlockState(pos);
                    if (state.isAir()) continue;
                    String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    var rule = BlockLightRules.rule(id);
                    if (rule == null || rule.intensity() <= 0) continue;
                    int emission = rule.emission() == null ? state.getLightEmission() : rule.emission();
                    if (emission <= 0) continue;
                    float radius = BlockLightRules.tintRadius(rule, (float)cfg.dynamicLightRadius, emission);
                    if (radius <= 0) continue;
                    additions.computeIfAbsent(key, k -> new ArrayList<>()).add(
                        new LightSource(x + .5, y + .5, z + .5,
                            radius, rule.intensity(), rule.color()));
                    positions.add(pos.asLong());
                }
            }
        }
        for (var slot : additions.entrySet()) {
            Entry old = indexed.get(slot.getKey());
            if (old == null) continue;
            List<LightSource> merged = new ArrayList<>(old.lights());
            merged.addAll(slot.getValue());
            List<LightSource> updated = List.copyOf(merged);
            indexed.put(slot.getKey(), new Entry(updated, old.scannedTick()));
            buckets.replace(slot.getKey().x, slot.getKey().z, updated);
            revision++;
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
