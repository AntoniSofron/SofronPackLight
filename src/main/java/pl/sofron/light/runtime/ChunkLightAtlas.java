package pl.sofron.light.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import pl.sofron.light.engine.LightSource;

/** Visible-chunk RGB atlas. R32_SINT texel buffer; all float payloads are raw IEEE754 bits.
 * Layout: 8 ints of metadata, 2 ints per chunk, 8 ints per light.
 * Chunk cells may point to identical light records repeatedly: this is intentional for now.
 * Zero GPU sources outside the indexed area; does not force chunk loading.
 */
public final class ChunkLightAtlas {
    public static final int HEADER_WORDS = 8;
    public static final int LIGHT_WORDS = 8;
    private static final int MAX_BUFFER_BYTES = 64 * 1024 * 1024;
    private ChunkLightAtlas() {}
    public record Snapshot(ByteBuffer bytes, int chunkCount, int lightReferences) {}

    public static Snapshot build(ChunkLightIndex index, int originX, int originZ,
                                 int width, double cameraX, double cameraY, double cameraZ) {
        return build(index,originX,originZ,width,cameraX,cameraY,cameraZ,null);
    }
    public static Snapshot build(ChunkLightIndex index, int originX, int originZ,
                                 int width, double cameraX, double cameraY, double cameraZ, DynamicVoxelField.Snapshot dynamic) {
        if (width < 1 || width > 129) throw new IllegalArgumentException("width must be 1..129");
        long n = (long) width * width;
        long words = HEADER_WORDS + n * 2;
        if (words * 4 > MAX_BUFFER_BYTES) throw new IllegalArgumentException("chunk grid too large");
        List<List<LightSource>> cells = new ArrayList<>((int)n);
        for (int z=0;z<width;z++) for (int x=0;x<width;x++) {
            List<LightSource> lights = index.lightsAffectingChunk(originX+x, originZ+z);
            cells.add(lights);
            words += (long)lights.size()*LIGHT_WORDS;
            if (words * 4 > MAX_BUFFER_BYTES) throw new IllegalStateException("Sofron RGB atlas exceeds 64 MiB; reduce render distance");
        }
        int dynamicOffset=(int)words;
        if(dynamic!=null)words+=4L+DynamicVoxelField.CELLS;
        if(words*4>MAX_BUFFER_BYTES)throw new IllegalStateException("RGB atlas too large");
        ByteBuffer b = ByteBuffer.allocateDirect((int)words*4).order(ByteOrder.nativeOrder());
        b.putInt(1).putInt(originX).putInt(originZ).putInt(width);
        b.putInt(Float.floatToRawIntBits((float)(cameraX - originX * 16.0)));
        b.putInt(Float.floatToRawIntBits((float)cameraY));
        b.putInt(Float.floatToRawIntBits((float)(cameraZ - originZ * 16.0)));
        b.putInt(dynamic==null?0:dynamicOffset);
        int offset = HEADER_WORDS + cells.size()*2;
        int refs = 0;
        for (List<LightSource> lights : cells) {
            b.putInt(offset).putInt(lights.size());
            offset += lights.size() * LIGHT_WORDS;
            refs += lights.size();
        }
        double x0 = originX * 16.0, z0 = originZ * 16.0;
        for (List<LightSource> lights : cells) for (LightSource s : lights) {
            putFloat(b, s.x()-x0); putFloat(b,s.y()); putFloat(b,s.z()-z0);
            putFloat(b,s.radius()); putFloat(b,s.color().red());
            putFloat(b,s.color().green()); putFloat(b,s.color().blue()); putFloat(b,s.power());
        }
        if(dynamic!=null)DynamicVoxelField.put(b,dynamic);
        b.flip();
        return new Snapshot(b, cells.size(), refs);
    }
    public static Snapshot empty(double cameraX,double cameraY,double cameraZ) {
        ByteBuffer b=ByteBuffer.allocateDirect((HEADER_WORDS+2)*4).order(ByteOrder.nativeOrder());
        b.putInt(1).putInt(0).putInt(0).putInt(1);
        b.putInt(Float.floatToRawIntBits((float)cameraX));
        b.putInt(Float.floatToRawIntBits((float)cameraY));
        b.putInt(Float.floatToRawIntBits((float)cameraZ));
        b.putInt(0).putInt(HEADER_WORDS+2).putInt(0);
        b.flip();
        return new Snapshot(b,1,0);
    }
    private static void putFloat(ByteBuffer b, double v) { b.putInt(Float.floatToRawIntBits((float)v)); }
}
