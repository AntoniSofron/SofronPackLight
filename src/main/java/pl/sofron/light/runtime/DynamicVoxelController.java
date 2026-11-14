package pl.sofron.light.runtime;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import pl.sofron.light.config.LightConfig;

/** Manages many dynamic emitters and a client-only voxel-light volume. */
public final class DynamicVoxelController {
    private final DynamicEmitterRegistry registry=new DynamicEmitterRegistry();
    private DynamicVoxelField.Snapshot field;
    private String previousKey="";
    private int age=0;
    public DynamicVoxelField.Snapshot field(){return field;}
    public DynamicEmitterRegistry registry(){return registry;}
    public void clear(){field=null;previousKey="";age=0;registry.clear();}
    public void tick(Minecraft client,LightConfig config){
        if(client.level==null || client.player==null || !config.dynamicLighting){clear();return;}
        registry.update(client,config);
        int x=client.player.blockPosition().getX(), y=client.player.blockPosition().getY()+1,
            z=client.player.blockPosition().getZ();
        // Quantize source positions to 1/8 block; update immediately on movement,
        // with a periodic refresh for doors, opaque blocks and world edits.
        StringBuilder key=new StringBuilder().append(x).append(':').append(y).append(':').append(z);
        List<DynamicVoxelField.Emitter> emitters=new ArrayList<>();
        for(var s:registry.sources()){
            key.append('|').append(Math.round(s.x()*8)).append(',').append(Math.round(s.y()*8))
               .append(',').append(Math.round(s.z()*8)).append(',').append(s.vanillaLevel())
               .append(',').append(s.red()).append(',').append(s.green()).append(',').append(s.blue())
               .append(',').append(s.tintRadius());
            // Flood-fill is block based; sub-block tracking still invalidates the
            // snapshot so shader / atlas updates don't become stale.
            emitters.add(new DynamicVoxelField.Emitter((int)Math.floor(s.x()),(int)Math.floor(s.y()),
               (int)Math.floor(s.z()),s.vanillaLevel(),s.red(),s.green(),s.blue(),s.tintRadius()));
        }
        String current=key.toString();
        if(field!=null && current.equals(previousKey) && ++age<5)return;
        age=0;previousKey=current;
        if(emitters.isEmpty()){field=null;return;}
        field=DynamicVoxelField.propagate(x,y,z,emitters,new MinecraftOcclusion(client.level));
    }
}
