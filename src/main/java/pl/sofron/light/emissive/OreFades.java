package pl.sofron.light.emissive;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.lighting.LevelLightEngine;

/** Mesh workers identify ore positions. Only the client thread reads live light and owns the animation table. */
public final class OreFades {
    private static final float DURATION = 0.65f;
    private static final Direction[] SIDES = Direction.values();
    private static volatile OreFades current = new OreFades(null);
    private final LevelLightEngine engine;
    private final long epoch = System.nanoTime();
    private final Map<Long, Section> sections = new ConcurrentHashMap<>();
    private final Map<Long, Fade> lit = new HashMap<>();
    private final AtomicBoolean needsUpdate = new AtomicBoolean(true);
    private volatile long playerSection;
    private int lastX, lastY, lastZ;
    private float nextExpiry = Float.POSITIVE_INFINITY;
    private float animationEnd;
    public float shaderTime() { return Math.min(seconds(), animationEnd); }
    private int age;
    private int lastDarken = -1;
    private ByteBuffer bytes = pack();

    private static final class Section {
        // One bit per local block. Mesh workers may report the same position concurrently.
        final AtomicLongArray positions = new AtomicLongArray(64);

        boolean add(BlockPos pos) {
            int word = ((pos.getY() & 15) << 2) | ((pos.getZ() & 15) >> 2);
            long bit = 1L << (((pos.getZ() & 3) << 4) | (pos.getX() & 15));
            long old;
            do {
                old = positions.get(word);
                if ((old & bit) != 0) return false;
            } while (!positions.compareAndSet(word, old, old | bit));
            return true;
        }
        final AtomicBoolean dirty = new AtomicBoolean(true);
        boolean active;
    }

    private record Fade(float start, float from, boolean on) {
        float value(float now) {
            float t = Math.clamp((now-start) / DURATION, 0f, 1f);
            return from + ((on ? 1f : 0f)-from) * t*t*(3f-2f*t);
        }
    }

    private OreFades(LevelLightEngine engine) { this.engine = engine; }
    public static OreFades current() { return current; }
    public float seconds() { return (System.nanoTime()-epoch)*1e-9f; }
    public ByteBuffer bytes() { return bytes; }

    public static void observe(LevelLightEngine engine, BlockPos pos) {
        var world = current;
        if (world.engine != engine) return;
        var section = world.sections.computeIfAbsent(SectionPos.asLong(pos), key -> new Section());
        if (section.add(pos)) {
            section.dirty.set(true);
            long player = world.playerSection;
            if (Math.abs((pos.getX() >> 4)-SectionPos.x(player)) <= 4
                    && Math.abs((pos.getY() >> 4)-SectionPos.y(player)) <= 4
                    && Math.abs((pos.getZ() >> 4)-SectionPos.z(player)) <= 4) world.needsUpdate.set(true);
        }
    }

    public static void lightChanged(LevelLightEngine engine, SectionPos pos) {
        var world = current;
        if (world.engine != engine) return;
        world.mark(pos.x(),pos.y(),pos.z());
        for (var side : SIDES) world.mark(pos.x()+side.getStepX(),pos.y()+side.getStepY(),pos.z()+side.getStepZ());
    }

    private void mark(int x, int y, int z) {
        var section = sections.get(SectionPos.asLong(x,y,z));
        if (section != null && !section.dirty.getAndSet(true)) needsUpdate.set(true);
    }

    public static void tick(Minecraft mc) {
        var level = mc.level;
        var engine = level == null ? null : level.getLightEngine();
        if (current.engine != engine) current = new OreFades(engine);
        if (level != null && mc.player != null) current.update(level, mc.player.blockPosition());
    }

    private void update(ClientLevel level, BlockPos player) {
        float now = seconds();
        int px = player.getX() >> 4, py = player.getY() >> 4, pz = player.getZ() >> 4;
        playerSection = SectionPos.asLong(px, py, pz);
        int darken = level.getSkyDarken();
        boolean changed = false;
        boolean prune = ++age % 40 == 0;
        boolean pending = needsUpdate.getAndSet(false);
        if (!pending && !prune && darken == lastDarken && px == lastX && py == lastY &&
                pz == lastZ && now < nextExpiry) return;
        lastX = px; lastY = py; lastZ = pz;
        // Enter/leave only on section boundaries. The shader performs the exact radial fade.
        for (var item : sections.entrySet()) {
            long key = item.getKey();
            var section = item.getValue();
            int x = SectionPos.x(key), y = SectionPos.y(key), z = SectionPos.z(key);
            boolean unloaded = prune && !level.hasChunk(x,z);
            boolean active = !unloaded && Math.abs(x-px)<=4 && Math.abs(y-py)<=4 && Math.abs(z-pz)<=4;
            boolean refresh = active && (section.dirty.getAndSet(false) || !section.active || darken != lastDarken);
            if (refresh || (!active && section.active)) {
                var block = new BlockPos.MutableBlockPos();
                var neighbor = new BlockPos.MutableBlockPos();
                for (int word = 0; word < 64; word++) {
                    long bits = section.positions.get(word);
                    while (bits != 0) {
                        int local = word*64 + Long.numberOfTrailingZeros(bits);
                        bits &= bits-1;
                        block.set(x*16+(local&15), y*16+(local>>8), z*16+((local>>4)&15));
                        long pos = block.asLong();
                        if (!active) {
                            changed |= lit.remove(pos) != null;
                            continue;
                        }
                        var start = lit.get(pos);
                        boolean on = illuminated(level, block, neighbor, darken);
                        if (on != (start != null && start.on)) {
                            lit.put(pos, new Fade(now, start == null ? 0f : start.value(now), on));
                            animationEnd = Math.nextUp(now + DURATION);
                            changed = true;
                        }
                    }
                }
            }
            section.active = active;
            if (unloaded) sections.remove(key,section);
        }
        lastDarken = darken;
        nextExpiry = Float.POSITIVE_INFINITY;
        for (Iterator<Fade> it = lit.values().iterator(); it.hasNext();) {
            Fade value = it.next();
            if (value.on) continue;
            float end = value.start + DURATION;
            if (now >= end) { it.remove(); changed = true; }
            else nextExpiry = Math.min(nextExpiry,end);
        }
        if (changed) bytes = pack();
    }

    private static boolean illuminated(ClientLevel level, BlockPos block, BlockPos.MutableBlockPos neighbor, int darken) {
        for (var side : SIDES) {
            neighbor.setWithOffset(block,side);
            if (level.getRawBrightness(neighbor,darken) >= 5) return true;
        }
        return false;
    }

    // Open-addressed coordinate table. Word 0 is the slot mask, each slot contains
    // x/y/z, state (0 empty, 1 dimming, 2 brightening), start time and starting opacity.
    private ByteBuffer pack() {
        int slots = 1;
        while (slots < lit.size()*2) slots *= 2;
        ByteBuffer out = ByteBuffer.allocateDirect((1+slots*6)*4).order(ByteOrder.nativeOrder());
        final int mask = slots-1;
        out.putInt(0,mask);
        lit.forEach((key,fade) -> {
            int x=BlockPos.getX(key), y=BlockPos.getY(key), z=BlockPos.getZ(key);
            int slot = (x*73856093 ^ y*19349663 ^ z*83492791) & mask;
            int offset = 4+slot*24;
            while (out.getInt(offset+12)!=0) {
                slot = (slot+1) & mask;
                offset = 4+slot*24;
            }
            out.putInt(offset,x).putInt(offset+4,y).putInt(offset+8,z);
            out.putInt(offset+12,fade.on ? 2 : 1).putFloat(offset+16,fade.start).putFloat(offset+20,fade.from);
        });
        return out;
    }
}
