package pl.sofron.light.runtime;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import pl.sofron.light.engine.LightSource;

/** Independently cached columns. Sparse sections keep exact sources, crowded sections
 * store the accumulated RGB field on a two-block lattice (nine nodes per axis). */
public final class ChunkLightAtlas {
    private static final int GRID = 9;
    private static final int MAX_WORDS = 16 * 1024 * 1024;
    private record Column(List<LightSource> lights, int[] words) {}
    private final Map<Long,Column> cached = new HashMap<>();
    public record Snapshot(ByteBuffer bytes, int chunkCount, int lightReferences) {}
    public void clear() { cached.clear(); }

    public Snapshot build(ChunkLightIndex index, int originX, int originZ, int width) {
        if (width < 1 || width > 129) throw new IllegalArgumentException("Invalid light atlas width");
        int cells = width*width;
        int size = 1+cells;
        int references = 0;
        Column[] columns = new Column[cells];
        cached.keySet().removeIf(key -> {
            int x = (int)(key >> 32)-originX, z = key.intValue()-originZ;
            return x < 0 || z < 0 || x >= width || z >= width;
        });
        for (int z=0; z<width; z++) for (int x=0; x<width; x++) {
            int cx = originX+x, cz = originZ+z;
            long key = ((long)cx << 32) | (cz & 0xffffffffL);
            List<LightSource> lights = index.lightsAffectingChunk(cx,cz);
            if (lights.isEmpty()) { cached.remove(key); continue; }
            Column column = cached.get(key);
            if (column == null || !column.lights.equals(lights)) {
                column = new Column(lights, encode(lights,cx*16,cz*16));
                cached.put(key,column);
            }
            columns[z*width+x] = column;
            references += lights.size();
            size += column.words.length;
            if (size > MAX_WORDS) throw new IllegalStateException("Light atlas exceeds 64 MiB");
        }
        ByteBuffer out = ByteBuffer.allocateDirect(size*4).order(ByteOrder.nativeOrder());
        var words = out.asIntBuffer();
        words.put(width);
        int next = 1+cells;
        for (Column column : columns) {
            words.put(column == null ? 0 : next);
            if (column != null) next += column.words.length;
        }
        for (Column column : columns) if (column != null) words.put(column.words);
        return new Snapshot(out,cells,references);
    }

    private static int[] encode(List<LightSource> lights, int x0, int z0) {
        var sections = new TreeMap<Integer,List<LightSource>>();
        for (LightSource light : lights) {
            int low = (int)Math.floor((light.y()-light.radius())/16);
            int high = (int)Math.floor((light.y()+light.radius())/16);
            for (int y=low; y<=high; y++) sections.computeIfAbsent(y,k -> new ArrayList<>()).add(light);
        }
        int low = sections.firstKey(), height = sections.lastKey()-low+1;
        IntArrayList out = new IntArrayList();
        out.size(2+height);
        out.set(0,low);
        out.set(1,height);
        sections.forEach((section, local) -> {
            out.set(2+section-low,out.size());
            int y0 = section*16;
            if (local.size() > 12 && local.stream().allMatch(s -> s.radius() >= 2)) {
                out.add(-1);
                bake(out,local,x0,y0,z0);
            } else {
                out.add(local.size());
                for (LightSource s : local) {
                    put(out,s.x()-x0); put(out,s.y()-y0); put(out,s.z()-z0); put(out,s.radius());
                    put(out,s.color().red()); put(out,s.color().green()); put(out,s.color().blue()); put(out,s.power());
                }
            }
        });
        return out.toIntArray();
    }

    private static void bake(IntArrayList out, List<LightSource> lights, int x0, int y0, int z0) {
        float[] sums = new float[GRID*GRID*GRID*5];
        for (LightSource s : lights) {
            double x=s.x()-x0, y=s.y()-y0, z=s.z()-z0, radius=s.radius();
            var c = s.color();
            float peak = Math.max(c.red(),Math.max(c.green(),c.blue()));
            if (peak<=0 || s.power()<=0) continue;
            int xa=first(x,radius), xb=last(x,radius), ya=first(y,radius), yb=last(y,radius);
            for (int k=first(z,radius); k<=last(z,radius); k++) for (int j=ya; j<=yb; j++) for (int i=xa; i<=xb; i++) {
                double dx=2*i-x, dy=2*j-y, dz=2*k-z, sq=dx*dx+dy*dy+dz*dz;
                if (sq >= radius*radius) continue;
                float influence = s.power()*(float)Math.pow(1-Math.sqrt(sq)/radius,2.8);
                float weight = influence*influence;
                int at=5*((k*GRID+j)*GRID+i);
                sums[at]+=c.red()*weight; sums[at+1]+=c.green()*weight; sums[at+2]+=c.blue()*weight;
                sums[at+3]+=peak*weight; sums[at+4]+=peak*influence;
            }
        }
        for (int i=0; i<sums.length; i+=5) {
            float weight=sums[i+3];
            int rgb=0;
            if (weight>1e-5f) {
                for (int k=0;k<3;k++) rgb |= Math.min(255,Math.round(255*sums[i+k]/weight)) << (k*8);
            }
            out.add(rgb);
            put(out,weight>1e-5f ? sums[i+4] : 0);
        }
    }

    private static int first(double center,double radius) { return Math.max(0,(int)Math.ceil((center-radius)/2)); }
    private static int last(double center,double radius) { return Math.min(GRID-1,(int)Math.floor((center+radius)/2)); }
    private static void put(IntArrayList out,double value) { out.add(Float.floatToRawIntBits((float)value)); }
    public static Snapshot empty() {
        return new Snapshot(ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()),0,0);
    }
}
