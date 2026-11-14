package pl.sofron.light.emissive;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** One immutable snapshot per block atlas, shared with Sodium's chunk mesh workers. */
public record OreMasks(Map<TextureAtlasSprite, TextureAtlasSprite> pairs, ByteBuffer rectangles) {
    private static volatile OreMasks current = new OreMasks(Map.of(), allocate(4).putInt(0, 0));
    public static OreMasks current() { return current; }

    private static final String ALIAS_PREFIX = "ore_mask_alias/";
    public static Identifier maskAlias(Identifier base) {
        return Identifier.fromNamespaceAndPath("sofron_pack_light", ALIAS_PREFIX + base.getNamespace() + "/" + base.getPath());
    }

    public static void rebuild(SpriteLoader.Preparations atlas) {
        Map<TextureAtlasSprite, TextureAtlasSprite> pairs = new HashMap<>();
        atlas.regions().forEach((id, mask) -> {
            String path = id.getPath();
            Identifier baseId;
            if (id.getNamespace().equals("sofron_pack_light") && path.startsWith(ALIAS_PREFIX)) {
                String[] parts = path.substring(ALIAS_PREFIX.length()).split("/", 2);
                if (parts.length != 2) return;
                baseId = Identifier.tryBuild(parts[0], parts[1]);
                if (baseId == null) return;
            } else {
                if (!path.endsWith("_ore_e")) return;
                baseId = id.withPath(path.substring(0, path.length() - 2));
            }
            var base = atlas.regions().get(baseId);
            if (base != null) pairs.put(base, mask);
        });
        ByteBuffer data = allocate(4 + 32 * pairs.size());
        data.putInt(pairs.size());
        pairs.forEach((base, mask) -> {
            putRect(data, base);
            putRect(data, mask);
        });
        current = new OreMasks(Map.copyOf(pairs), data.flip().asReadOnlyBuffer());
    }

    private static ByteBuffer allocate(int size) {
        return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    }

    private static void putRect(ByteBuffer data, TextureAtlasSprite sprite) {
        data.putFloat(sprite.getU0()).putFloat(sprite.getV0());
        data.putFloat(sprite.getU1()).putFloat(sprite.getV1());
    }
}
