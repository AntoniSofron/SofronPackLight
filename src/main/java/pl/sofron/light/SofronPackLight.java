package pl.sofron.light;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import pl.sofron.light.engine.BlockLightRules;

public final class SofronPackLight implements ModInitializer {
    @Override public void onInitialize() {
        BlockLightRules.initialize(FabricLoader.getInstance().getConfigDir().resolve("sofron-pack-light-blocks.properties"));
    }
}
