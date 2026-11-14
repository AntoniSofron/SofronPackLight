package pl.sofron.light.render;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import pl.sofron.light.engine.LightSource;

/** Immutable publication boundary for the world update and rendering threads.
 *  No render thread may read a list being mutated by the world tracker.
 *  This avoids partial frames, but does not by itself integrate a renderer.
 */
public final class FrameExchange {
    public record Snapshot(long generation, List<LightSource> sources) {}
    private final AtomicReference<Snapshot> current = new AtomicReference<>(new Snapshot(0, List.of()));

    public void publish(List<LightSource> sources) {
        if (sources == null) throw new IllegalArgumentException("sources");
        var immutable = List.copyOf(sources);
        current.updateAndGet(before -> new Snapshot(before.generation() + 1, immutable));
    }
    public Snapshot read() { return current.get(); }
    public void clear() { publish(List.of()); }
}
