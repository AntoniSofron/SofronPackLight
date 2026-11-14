package pl.sofron.light.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.core.registries.BuiltInRegistries;
import pl.sofron.light.config.LightConfig;
import pl.sofron.light.engine.BlockLightRules;
import pl.sofron.light.engine.RgbLight;

/** Client-only emitter discovery. Never places world blocks or changes server lighting. */
public final class DynamicEmitterRegistry {
    public record Source(double x, double y, double z, int vanillaLevel,
                         int red, int green, int blue, int tintRadius) {}
    private volatile List<Source> sources = List.of();
    public List<Source> sources() { return sources; }
    public void clear() { sources = List.of(); }

    public void update(Minecraft client, LightConfig cfg) {
        if (client.player == null || client.level == null || !cfg.dynamicLighting) {clear();return;}
        var player=client.player;
        List<Source> collected=new ArrayList<>();
        if (cfg.heldItems) {
            add(collected,player.getMainHandItem(),player.getX(),player.getY()+1.1,player.getZ(),cfg);
            add(collected,player.getOffhandItem(),player.getX(),player.getY()+1.1,player.getZ(),cfg);
        }
        if(cfg.entityAndItemLighting) {
            for(Entity entity:client.level.entitiesForRendering()) {
                if(entity==player || entity.distanceToSqr(player.getX(),player.getY(),player.getZ())>28*28)continue;
                double x=entity.getX(), y=entity.getY()+entity.getBbHeight()*.55, z=entity.getZ();
                if(cfg.burningEntities && entity.isOnFire())
                    collected.add(new Source(x,y,z,12,255,108,31,3));
                if(cfg.droppedItems && entity instanceof ItemEntity drop)
                    add(collected,drop.getItem(),x,entity.getY()+.2,z,cfg);
                if(cfg.heldItems && entity instanceof LivingEntity living) {
                    add(collected,living.getMainHandItem(),x,y,z,cfg);
                    add(collected,living.getOffhandItem(),x,y,z,cfg);
                }
            }
        }
        collected.sort(Comparator.comparingDouble(s -> squared(s.x()-player.getX(),s.y()-player.getY(),s.z()-player.getZ())));
        // Limit only emitters contributing to the local 33^3 voxel window.
        sources=List.copyOf(collected.subList(0,Math.min(collected.size(),64)));
    }
    private static double squared(double x,double y,double z){return x*x+y*y+z*z;}
    private static void add(List<Source> result,ItemStack item,double x,double y,double z,LightConfig cfg){
        if(item.isEmpty())return;
        String id=BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        var rule=BlockLightRules.rule(id);
        if(!BlockLightRules.isTracked(id) && !(item.getItem() instanceof BlockItem bi && bi.getBlock().defaultBlockState().getLightEmission()>0))return;
        int emission=rule!=null && rule.emission()!=null ? rule.emission() :
          item.getItem() instanceof BlockItem bi ? bi.getBlock().defaultBlockState().getLightEmission() : 14;
        if(emission<=0)return;
        RgbLight hue=rule!=null && cfg.coloredLighting?rule.color():BlockLightRules.neutral();
        float power=rule!=null && cfg.coloredLighting ? rule.intensity():0f;
        int radius=rule==null?0:Math.max(0,Math.min(15,Math.round(BlockLightRules.tintRadius(rule,0f,emission))));
        result.add(new Source(x,y,z,Math.min(emission,15),
            Math.round(255*hue.red()*power),Math.round(255*hue.green()*power),Math.round(255*hue.blue()*power),radius));
    }
}
