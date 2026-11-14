package pl.sofron.light.engine;

import java.util.Collection;

/** Legacy unoccluded sampling entry point. Renderer integration is not included. */
public final class RgbLightSampler {
    private RgbLightSampler() {}
    public static RgbLight sample(double x,double y,double z,Collection<LightSource> lights,float multiplier){
        return RgbLightField.sample(x,y,z,lights,multiplier,LightOcclusion.NONE);
    }
}
