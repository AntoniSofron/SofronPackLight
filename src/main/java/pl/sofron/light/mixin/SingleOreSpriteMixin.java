package pl.sofron.light.mixin;

import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.sources.SingleFile;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.sofron.light.emissive.OreMasks;

/** Atlas single entries can load a block from a directory outside textures/block or under an alias. */
@Mixin(SingleFile.class)
abstract class SingleOreSpriteMixin {
    @Inject(method = "run", at = @At("TAIL"))
    private void sofron$addMask(ResourceManager manager, SpriteSource.Output output, CallbackInfo ci) {
        var source = (SingleFile)(Object)this;
        var base = source.resourceId();
        if (!base.getPath().endsWith("_ore")) return;
        Identifier mask = base.withPath(base.getPath() + "_e");
        var alias = source.spriteId().orElse(base);
        var sprite = alias.equals(base) ? mask : OreMasks.maskAlias(alias);
        manager.getResource(SpriteSource.TEXTURE_ID_CONVERTER.idToFile(mask))
               .ifPresent(resource -> output.add(sprite, resource));
    }
}
