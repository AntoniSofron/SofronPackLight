package pl.sofron.light.engine;

import java.util.Set;
/** Known vanilla luminous blocks/items. Their default output is NEUTRAL, not RGB. */
public final class LightPalette {
    private LightPalette() {}
    public static final Set<String> LUMINOUS = Set.of(
        "minecraft:copper_torch", "minecraft:copper_wall_torch", "minecraft:copper_lantern",
        "minecraft:torch", "minecraft:wall_torch", "minecraft:lantern",
        "minecraft:soul_torch", "minecraft:soul_wall_torch", "minecraft:soul_lantern",
        "minecraft:redstone_torch", "minecraft:redstone_wall_torch", "minecraft:redstone_lamp",
        "minecraft:glowstone", "minecraft:sea_lantern", "minecraft:shroomlight",
        "minecraft:ochre_froglight", "minecraft:verdant_froglight", "minecraft:pearlescent_froglight",
        "minecraft:jack_o_lantern", "minecraft:end_rod", "minecraft:campfire", "minecraft:soul_campfire",
        "minecraft:lava", "minecraft:magma_block", "minecraft:beacon", "minecraft:glow_lichen",
        "minecraft:crying_obsidian", "minecraft:respawn_anchor", "minecraft:enchanting_table"
    );
}
