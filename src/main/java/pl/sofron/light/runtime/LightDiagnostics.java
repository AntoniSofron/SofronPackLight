package pl.sofron.light.runtime;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import pl.sofron.light.config.LightConfig;
import pl.sofron.light.engine.BlockLightRules;
import pl.sofron.light.engine.LightSource;
import pl.sofron.light.render.gpu.SodiumRenderPassCoordinator;

/** Lightweight client-side report; values represent snapshots, not GPU readback. */
public final class LightDiagnostics {
    private final Path report;
    private int ticks;
    public LightDiagnostics(Path report) { this.report=report; }
    public void tick(Minecraft client, LightTracker tracker, LightConfig cfg) {
        if (++ticks % 200 != 0 || client.player == null || client.level == null) return;
        ChunkLightIndex index = tracker.chunkIndex();
        StringBuilder b = new StringBuilder();
        b.append("Sofron Pack Light RGB diagnostics (0.9.24)\n")
         .append("Updated every 200 client ticks (approximately 10 seconds).\n")
         .append("This is CPU-side state + atlas submission counters; NOT a GPU pixel readback.\n\n")
         .append("coloredLighting=").append(cfg.coloredLighting).append('\n')
         .append("dynamicLighting=").append(cfg.dynamicLighting).append('\n')
         .append("stationaryBlockLighting=").append(cfg.stationaryBlockLighting).append('\n')
         .append("activeBlockRules=").append(BlockLightRules.ruleCount()).append('\n')
         .append("ruleRevision=").append(BlockLightRules.revision()).append('\n')
         .append("indexedChunks=").append(index.indexedChunks()).append('\n')
         .append("indexedColoredSources=").append(index.indexedLights()).append('\n')
         .append("atlasChunkCells=").append(SodiumRenderPassCoordinator.diagnosticAtlasCells()).append('\n')
         .append("atlasLightReferences=").append(SodiumRenderPassCoordinator.diagnosticAtlasReferences()).append('\n')
         .append("atlasOverflowFrames=").append(SodiumRenderPassCoordinator.diagnosticAtlasFailures()).append('\n')
         .append("renderDrawBindings=").append(SodiumRenderPassCoordinator.diagnosticDrawSubmissions()).append('\n')
         .append("movingSourcesInUniform=").append(tracker.movingCount()).append('\n')
         .append("renderDistanceChunks=").append(client.options.renderDistance().get()).append('\n');
        int samples = 0;
        b.append("\nFirst indexed sources (location, RGB, intensity, radius):\n");
        for (LightSource source : index.allLights()) {
            if (samples++ >= 12) break;
            b.append(String.format(java.util.Locale.ROOT,
                "  %.1f %.1f %.1f  rgb=(%.3f, %.3f, %.3f) power=%.2f radius=%.1f\n",
                source.x(),source.y(),source.z(),source.color().red(),source.color().green(),
                source.color().blue(),source.power(),source.radius()));
        }
        try {
            Files.createDirectories(report.getParent());
            Files.writeString(report,b,StandardCharsets.UTF_8);
        } catch (IOException ex) {
            System.err.println("[Sofron Light] Diagnostic report write failed: " + ex.getMessage());
        }
    }
}
