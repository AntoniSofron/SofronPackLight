package pl.sofron.light.engine;

import java.util.Collection;

/**
 * CPU reference renderer input: additive RGB illumination with voxel ray occlusion.
 * Samples can later be consumed by a native rendering backend; this class does not draw pixels.
 */
public final class RgbLightField {
    private RgbLightField() {}

    public static RgbLight sample(double x, double y, double z,
                                  Collection<LightSource> sources, float multiplier,
                                  LightOcclusion world) {
        if (world == null) throw new IllegalArgumentException("world is null");
        if (sources == null) throw new IllegalArgumentException("sources is null");
        double r = 0, g = 0, b = 0;
        double scale = Math.max(0, multiplier);
        for (LightSource src : sources) {
            double dx = x - src.x(), dy = y - src.y(), dz = z - src.z();
            double dsq = dx*dx + dy*dy + dz*dz;
            if (dsq >= src.radius()*src.radius()) continue;
            if (blocked(src.x(), src.y(), src.z(), x, y, z, world)) continue;
            double falloff = 1 - Math.sqrt(dsq)/src.radius();
            double amount = falloff * falloff * src.power() * scale;
            r += src.color().red()*amount;
            g += src.color().green()*amount;
            b += src.color().blue()*amount;
        }
        // Saturation at final output (not per source) preserves proper additive mixing.
        return new RgbLight((float)r,(float)g,(float)b);
    }

    /** 3D voxel traversal; endpoint cells aren't tested, so a source and surface may be solid. */
    static boolean blocked(double x0,double y0,double z0,double x1,double y1,double z1, LightOcclusion world) {
        int ix=floor(x0), iy=floor(y0), iz=floor(z0);
        int ex=floor(x1), ey=floor(y1), ez=floor(z1);
        if(ix==ex && iy==ey && iz==ez) return false;
        double dx=x1-x0,dy=y1-y0,dz=z1-z0;
        int sx=Integer.compare(ex,ix),sy=Integer.compare(ey,iy),sz=Integer.compare(ez,iz);
        double tx=initT(x0,dx,ix,sx),ty=initT(y0,dy,iy,sy),tz=initT(z0,dz,iz,sz);
        double ax=sx==0?Double.POSITIVE_INFINITY:Math.abs(1/dx);
        double ay=sy==0?Double.POSITIVE_INFINITY:Math.abs(1/dy);
        double az=sz==0?Double.POSITIVE_INFINITY:Math.abs(1/dz);
        // Conservative tie handling visits cells crossed along edges simultaneously.
        for(int iterations=0;iterations<4096;iterations++) {
            double t=Math.min(tx,Math.min(ty,tz));
            if(t>1 || !Double.isFinite(t)) return false;
            if(tx<=t+1e-10) {ix+=sx;tx+=ax;}
            if(ty<=t+1e-10) {iy+=sy;ty+=ay;}
            if(tz<=t+1e-10) {iz+=sz;tz+=az;}
            if(ix==ex && iy==ey && iz==ez) return false;
            if(world.blocks(ix,iy,iz)) return true;
        }
        throw new IllegalArgumentException("Ray exceeded safe traversal limit");
    }
    private static int floor(double n){return (int)Math.floor(n);}
    private static double initT(double start,double delta,int cell,int step){
        return step==0 ? Double.POSITIVE_INFINITY : ((step>0?cell+1:cell)-start)/delta;
    }
}
