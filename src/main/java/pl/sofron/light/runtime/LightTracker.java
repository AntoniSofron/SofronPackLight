package pl.sofron.light.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import pl.sofron.light.config.LightConfig;
import pl.sofron.light.engine.LightPalette;
import pl.sofron.light.engine.BlockLightRules;
import pl.sofron.light.engine.LightSource;
import pl.sofron.light.engine.RgbLight;

/** Client tick snapshot. Does not alter vanilla block states or network data. */
public final class LightTracker {
    private volatile List<LightSource> sources = List.of();
    private volatile int movingCount = 0;
    public int movingCount() { return movingCount; }
    private final ChunkLightIndex chunkIndex = new ChunkLightIndex();
    public ChunkLightIndex chunkIndex() { return chunkIndex; }

    // Keep sources near the 256-light cutoff stable between adjacent ticks.
    private final Set<String> previousSelected = new HashSet<>();

    public List<LightSource> sources() { return sources; }
    public void reset() {
        sources = List.of();
        movingCount = 0;
        chunkIndex.clear();
        previousSelected.clear();

    }

    public void tick(Minecraft client, LightConfig cfg) {
        if (!cfg.dynamicLighting || client.player == null || client.level == null) {
            reset(); return;
        }
        BlockLightRules.poll();
        var player = client.player;
        var found = new ArrayList<LightSource>();
        double px = player.getX(), py = player.getY(), pz = player.getZ();
        double maxDistanceSquared = 48 * 48;
        // Both hands of the local player are always included.
        if (cfg.heldItems) {
            addStack(found, player.getMainHandItem(), px, py + 1.1, pz, cfg);
            addStack(found, player.getOffhandItem(), px, py + 1.1, pz, cfg);
        }
        if (cfg.entityAndItemLighting) {
            for (Entity entity : client.level.entitiesForRendering()) {
                double x = entity.getX(), y = entity.getY(), z = entity.getZ();
                if (entity.distanceToSqr(px, py, pz) > maxDistanceSquared) continue;
                if (cfg.burningEntities && entity.isOnFire()) {
                    found.add(new LightSource(x, y + entity.getBbHeight() * .5, z,
                        (float)cfg.dynamicLightRadius, .9f, new RgbLight(1f,.42f,.12f)));
                }
                if (cfg.droppedItems && entity instanceof ItemEntity drop) {
                    addStack(found, drop.getItem(), x, y + .15, z, cfg);
                }
                if (cfg.heldItems && entity instanceof LivingEntity living && entity != player) {
                    addStack(found, living.getMainHandItem(), x, y + living.getBbHeight()*.6, z, cfg);
                    addStack(found, living.getOffhandItem(), x, y + living.getBbHeight()*.6, z, cfg);
                }
            }
        }
        int movingEnd = found.size();
        // Vanilla lighting has brightness but no RGB channels. Stationary light blocks
        // must be supplied separately to our RGB renderer, like held items.
        if (cfg.stationaryBlockLighting) {
            chunkIndex.tick(client, cfg);
            // Compatibility with the existing single-buffer GPU renderer: still only
            // the nearest 256 sources can be uploaded. The full chunk index is retained
            // for a future chunk-local render binding stage.
            found.addAll(chunkIndex.allLights());
        } else {
            chunkIndex.clear();
        }
        // Stable selection: the previous frame's nearby lights get a small score
        // advantage, preventing rapid swapping at the 256-source GPU limit.
        // Keep the distance score dominant so old lights do not stay forever.
        // Moving lights are prioritized: an abundance of stationary lights must never
        // evict the torch held by the player from the 256-entry GPU buffer.
        Comparator<LightSource> priority = Comparator
            .comparingDouble((LightSource light) -> {
                double distance = squared(light.x()-px, light.y()-py, light.z()-pz);
                return distance - (previousSelected.contains(sourceKey(light)) ? 9.0 : 0.0);
            })
            .thenComparingDouble(LightSource::x)
            .thenComparingDouble(LightSource::y)
            .thenComparingDouble(LightSource::z);
        found.subList(0, movingEnd).sort(priority);
        found.subList(movingEnd, found.size()).sort(priority);
        int count = Math.min(found.size(), Math.min(cfg.maximumDynamicLights, 256));
        movingCount = Math.min(movingEnd, count);
        List<LightSource> selected = List.copyOf(found.subList(0, count));
        previousSelected.clear();
        for (LightSource light : selected) previousSelected.add(sourceKey(light));
        sources = selected;
    }
    private static String sourceKey(LightSource source) {
        // Block centres are exact half-integers; moving sources are bucketed
        // to half-block positions to avoid retaining a stale moving coordinate.
        return Math.round(source.x()*2) + ":" + Math.round(source.y()*2) + ":" + Math.round(source.z()*2);
    }
    private static double squared(double x,double y,double z) { return x*x+y*y+z*z; }
    private static void addStack(List<LightSource> list, ItemStack stack,
                                 double x, double y, double z, LightConfig cfg) {
        if (stack.isEmpty()) return;
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (!BlockLightRules.isTracked(id)
            && !(stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock().defaultBlockState().getLightEmission() > 0)) return;
        var rule = BlockLightRules.rule(id);
        RgbLight rgb = cfg.coloredLighting && rule != null ? rule.color() : BlockLightRules.neutral();
        float power = rule == null ? 1f : rule.intensity();
        int emission = rule != null && rule.emission() != null ? rule.emission() :
            stack.getItem() instanceof BlockItem blockItem ? blockItem.getBlock().defaultBlockState().getLightEmission() : 15;
        float radius = BlockLightRules.tintRadius(rule, (float)cfg.dynamicLightRadius, emission);
        if (radius <= 0f || power <= 0f) return;
        list.add(new LightSource(x,y,z,radius,power,rgb));
    }
}
