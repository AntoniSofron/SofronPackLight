package pl.sofron.light.runtime;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;

/** Omit leaf detail geometry from distant new meshes. Rebuild those sections
 * ahead of the exact eight-chunk visibility boundary controlled by the shader. */
public final class LeafDetailRange {
    private static ClientLevel level;
    private static int playerX, playerZ;
    private static final LongOpenHashSet omitted = new LongOpenHashSet();
    private static int ticks;

    private LeafDetailRange() {}

    private static boolean distant(int x, int z) {
        int dx = Math.max(0, Math.abs(x-playerX)-1);
        int dz = Math.max(0, Math.abs(z-playerZ)-1);
        return (long)dx*dx + (long)dz*dz > 9*9;
    }

    public static synchronized boolean distant(ClientLevel world, BlockPos pos) {
        return world == level && distant(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public static synchronized boolean register(ClientLevel world, BlockPos pos) {
        if (!distant(world, pos)) return false;
        omitted.add(SectionPos.asLong(pos));
        return true;
    }

    public static synchronized void tick(Minecraft mc) {
        var world = mc.player == null ? null : mc.level;
        boolean reset = world != level;
        if (reset) { omitted.clear(); level = world; }
        if (world == null) return;
        int x = mc.player.blockPosition().getX() >> 4, z = mc.player.blockPosition().getZ() >> 4;
        boolean prune = ++ticks % 80 == 0;
        if (!reset && x == playerX && z == playerZ && !prune) return;
        playerX=x; playerZ=z;
        var renderer = SodiumWorldRenderer.instanceNullable();
        if (renderer == null) return;
        var iter = omitted.iterator();
        while (iter.hasNext()) {
            long key = iter.nextLong();
            int sx = SectionPos.x(key), sz = SectionPos.z(key);
            if (!distant(sx, sz)) {
                renderer.scheduleRebuildForChunk(sx, SectionPos.y(key), sz, true);
                iter.remove();
            } else if (prune && !world.hasChunk(sx, sz)) iter.remove();
        }
    }
}
