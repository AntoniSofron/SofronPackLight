package pl.sofron.light.emissive;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Collections;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** One immutable snapshot per block atlas, shared with Sodium's chunk mesh workers. */
public record OreMasks(Map<TextureAtlasSprite, TextureAtlasSprite> pairs, Set<TextureAtlasSprite> leafDetails, ByteBuffer rectangles) {
    private static volatile OreMasks current = new OreMasks(Map.of(), Set.of(), allocate(4).putInt(0, 0));
    public static OreMasks current() { return current; }

    private static final Set<SpriteContents> leafAliases =
        Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    public static boolean isLeafDetail(String path) {
        return path.endsWith("_leaves_bottom") || path.endsWith("_leaves_top");
    }

    public static void markLeafAlias(SpriteContents contents) {
        if (contents != null) leafAliases.add(contents);
    }

    private static final String ALIAS_PREFIX = "ore_mask_alias/";
    public static Identifier maskAlias(Identifier base) {
        return Identifier.fromNamespaceAndPath("sofron_pack_light", ALIAS_PREFIX + base.getNamespace() + "/" + base.getPath());
    }

    public static void rebuild(SpriteLoader.Preparations atlas) {
        Map<TextureAtlasSprite, TextureAtlasSprite> pairs = new HashMap<>();
        Set<TextureAtlasSprite> leaves = new HashSet<>();
        atlas.regions().forEach((id, mask) -> {
            String path = id.getPath();
            if (isLeafDetail(path) || leafAliases.contains(mask.contents())) leaves.add(mask);
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
        current = new OreMasks(Map.copyOf(pairs), Set.copyOf(leaves), tileIndex(atlas, pairs));
    }

    // Sparse lists per texture tile. A fragment examines only sprites sharing that tile.
    private static ByteBuffer tileIndex(SpriteLoader.Preparations atlas, Map<TextureAtlasSprite, TextureAtlasSprite> pairs) {
        if (pairs.isEmpty()) return allocate(4).putInt(0, 0);
        int tile = Math.max(16, Math.max(atlas.width(), atlas.height()) / 256);
        int columns = Math.ceilDiv(atlas.width(), tile);
        var cells = new java.util.ArrayList<java.util.ArrayList<Integer>>(
            java.util.Collections.nCopies(columns * Math.ceilDiv(atlas.height(), tile), null));
        int descriptor = 0;
        for (var base : pairs.keySet()) {
            int x0 = Math.round(base.getU0() * atlas.width()) / tile;
            int y0 = Math.round(base.getV0() * atlas.height()) / tile;
            int x1 = (Math.round(base.getU1() * atlas.width()) - 1) / tile;
            int y1 = (Math.round(base.getV1() * atlas.height()) - 1) / tile;
            for (int y=y0; y<=y1; y++) for (int x=x0; x<=x1; x++) {
                int index = y*columns+x;
                var list = cells.get(index);
                if (list == null) { list = new java.util.ArrayList<>(); cells.set(index, list); }
                list.add(descriptor);
            }
            descriptor++;
        }
        int entries = cells.stream().filter(java.util.Objects::nonNull).mapToInt(java.util.List::size).sum();
        int indices = 3 + cells.size()*2;
        int records = indices + entries;
        ByteBuffer data = allocate(4*(records + pairs.size()*8));
        data.putInt(tile).putInt(columns).putInt(records);
        for (var list : cells) {
            int count = list == null ? 0 : list.size();
            data.putInt(indices).putInt(count);
            indices += count;
        }
        for (var list : cells) if (list != null) for (int id : list) data.putInt(id);
        for (var entry : pairs.entrySet()) {
            putRect(data, entry.getKey());
            putRect(data, entry.getValue());
        }
        return data.flip().asReadOnlyBuffer();
    }

    private static ByteBuffer allocate(int size) {
        return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    }

    private static void putRect(ByteBuffer data, TextureAtlasSprite sprite) {
        data.putFloat(sprite.getU0()).putFloat(sprite.getV0());
        data.putFloat(sprite.getU1()).putFloat(sprite.getV1());
    }
}
