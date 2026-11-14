package pl.sofron.light.render.gpu;

/**
 * RenderPearl-safe ordering gate. Must be entered at actual terrain draw submission,
 * AFTER RenderPass.setPipeline, never when the pipeline is only constructed.
 * A legacy GL binding is deliberately NOT performed here: RenderPearl owns its
 * GPU command list and may defer program activation until submission.
 */
public final class RenderPassLightSync {
    public interface DrawBinding {
        /** Called after pipeline selection and immediately before queued draw. */
        boolean bindForDraw();
    }
    private boolean inside;
    private long attempts, accepted;
    public synchronized boolean beforeDraw(DrawBinding binding) {
        attempts++;
        if (inside || binding == null) return false;
        inside = true;
        try {
            boolean ok = binding.bindForDraw();
            if (ok) accepted++;
            return ok;
        } finally {
            inside = false;
        }
    }
    public synchronized long attempts() { return attempts; }
    public synchronized long accepted() { return accepted; }
}
