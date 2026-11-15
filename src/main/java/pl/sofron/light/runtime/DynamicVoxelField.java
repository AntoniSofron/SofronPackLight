package pl.sofron.light.runtime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import pl.sofron.light.engine.LightOcclusion;

/**
 * Experimental client-side voxel light solver. Independent from the stationary
 * source atlas; local brightness levels are propagated through non-opaque cells.
 * Coordinates are absolute integer block positions. No chunk loads are forced.
 */
public final class DynamicVoxelField {
    public static final int SIZE = 33;
    public static final int CELLS = SIZE * SIZE * SIZE;
    public record Emitter(int x, int y, int z, int level, int red, int green, int blue, int tintRange) {}
    public record Snapshot(int x0, int y0, int z0, int[] voxels) {
        public int sample(int x, int y, int z) {
            int dx=x-x0, dy=y-y0, dz=z-z0;
            return dx<0||dy<0||dz<0||dx>=SIZE||dy>=SIZE||dz>=SIZE ? 0 : voxels[index(dx,dy,dz)];
        }
    }
    private DynamicVoxelField() {}
    private static int index(int x,int y,int z) {return (y*SIZE+z)*SIZE+x;}
    private static final int[] DX={1,-1,0,0,0,0};
    private static final int[] DY={0,0,1,-1,0,0};
    private static final int[] DZ={0,0,0,0,1,-1};
    public static Snapshot propagate(int centerX,int centerY,int centerZ,
                                     List<Emitter> emitters, LightOcclusion occlusion) {
        int x0=centerX-SIZE/2, y0=centerY-SIZE/2, z0=centerZ-SIZE/2;
        int[] packed = new int[CELLS];
        byte[] obstruction = new byte[CELLS];
        byte[] seen = new byte[CELLS];
        int[] queue = new int[CELLS];
        for (Emitter emitter:emitters) {
            int sx=emitter.x-x0, sy=emitter.y-y0, sz=emitter.z-z0;
            if(sx<0||sy<0||sz<0||sx>=SIZE||sy>=SIZE||sz>=SIZE)continue;
            int origin=index(sx,sy,sz);
            int head=0, tail=0;
            int sourceLevel=Math.max(0,Math.min(15,emitter.level));
            if(sourceLevel==0)continue;
            seen[origin]=(byte)sourceLevel;
            queue[tail++]=origin;
            while(head < tail) {
                int at=queue[head++];
                int level=Byte.toUnsignedInt(seen[at]);
                int old=packed[at];
                int rgb=old & 0xffffff;
                int vx=at%SIZE, vy=at/(SIZE*SIZE), vz=(at/SIZE)%SIZE;
                int dist=sourceLevel - level;
                if(dist<=Math.max(0,emitter.tintRange)) {
                    // Preserve chromatic hue, distinct from the neutral brightness.
                    // Max aggregation avoids turning same-color clusters white.
                    float tintWeight=1f-(float)dist/(Math.max(1,emitter.tintRange)+1f);
                    int vr=Math.round(Math.max(0,Math.min(255,emitter.red))*tintWeight);
                    int vg=Math.round(Math.max(0,Math.min(255,emitter.green))*tintWeight);
                    int vb=Math.round(Math.max(0,Math.min(255,emitter.blue))*tintWeight);
                    rgb = (Math.max(vr,(old>>>16)&255)<<16) | (Math.max(vg,(old>>>8)&255)<<8)
                        | Math.max(vb,old&255);
                }
                packed[at]=(Math.max(level,old>>>24)<<24) | rgb;
                if(level<=1) continue;
                for(int face=0;face<6;face++) {
                    int nx=vx+DX[face],ny=vy+DY[face],nz=vz+DZ[face];
                    if(nx<0||ny<0||nz<0||nx>=SIZE||ny>=SIZE||nz>=SIZE)continue;
                    int next=index(nx,ny,nz);
                    int nextLevel=level-1;
                    if(Byte.toUnsignedInt(seen[next])>=nextLevel)continue;
                    if(obstruction[next]==0) obstruction[next]=(byte)(occlusion.blocks(x0+nx,y0+ny,z0+nz)?2:1);
                    if(obstruction[next]==2)continue;
                    seen[next]=(byte)nextLevel;
                    queue[tail++]=next;
                }
            }
            // Each cell enters the FIFO once per emitter in descending brightness order.
            for (int k=0; k<tail; k++) seen[queue[k]]=0;
        }
        return new Snapshot(x0,y0,z0,packed);
    }
    /** header (x0,y0,z0,size) followed by packed RGBA voxels */
    public static void put(ByteBuffer buffer, Snapshot snapshot) {
        buffer.order(ByteOrder.nativeOrder());
        buffer.putInt(snapshot.x0).putInt(snapshot.y0).putInt(snapshot.z0).putInt(SIZE);
        for(int voxel:snapshot.voxels)buffer.putInt(voxel);
    }
}
