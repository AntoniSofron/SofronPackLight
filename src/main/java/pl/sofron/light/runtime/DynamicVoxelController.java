package pl.sofron.light.runtime;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import pl.sofron.light.config.LightConfig;

/** Manages many dynamic emitters and a client-only voxel-light volume. */
public final class DynamicVoxelController {
    private final DynamicEmitterRegistry registry=new DynamicEmitterRegistry();
    private DynamicVoxelField.Snapshot field;
    private List<DynamicVoxelField.Emitter> previous = List.of();
    private int age=0;
    public DynamicVoxelField.Snapshot field(){return field;}
    public DynamicEmitterRegistry registry(){return registry;}
    public void clear(){field=null;previous=List.of();age=0;registry.clear();}
    public void tick(Minecraft client,LightConfig config){
        if(client.level==null || client.player==null || !config.dynamicLighting){clear();return;}
        registry.update(client,config);
        int x=client.player.blockPosition().getX(), y=client.player.blockPosition().getY()+1,
            z=client.player.blockPosition().getZ();
        if (registry.sources().isEmpty()) { field=null; previous=List.of(); age=0; return; }
        List<DynamicVoxelField.Emitter> emitters=new ArrayList<>();
        for(var s:registry.sources()){
            emitters.add(new DynamicVoxelField.Emitter((int)Math.floor(s.x()),(int)Math.floor(s.y()),
               (int)Math.floor(s.z()),s.vanillaLevel(),s.red(),s.green(),s.blue(),s.tintRadius()));
        }
        int half = DynamicVoxelField.SIZE / 2;
        if (field != null && field.x0() == x-half && field.y0() == y-half && field.z0() == z-half
                && previous.equals(emitters) && ++age < 5) return;
        age=0;
        previous=emitters;
        var next=DynamicVoxelField.propagate(x,y,z,emitters,new MinecraftOcclusion(client.level));
        // A periodic occlusion refresh often reproduces the existing volume exactly.
        if (field == null || field.x0()!=next.x0() || field.y0()!=next.y0() || field.z0()!=next.z0()
                || !java.util.Arrays.equals(field.voxels(), next.voxels())) field=next;
    }
}
