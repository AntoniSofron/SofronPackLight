package pl.sofron.light.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Redirects Sodium 0.9.2 terrain shader IDs to resources owned by this mod.
 * Covers standard terrain and OIT terrain pipeline constructors.
 * This is a shader *selection* hook; sampler binding and validation are separate.
 */
@Mixin(value = ShaderChunkRenderer.class, remap = false)
public abstract class SodiumShaderLoadingMixin {
    private static final Identifier SOFRON_SHADER =
        Identifier.fromNamespaceAndPath("sofron_pack_light", "blocks/block_layer_opaque");

    @ModifyArg(method = {"createShader", "createOITShader"},
        at = @At(value = "INVOKE", target =
            "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;withVertexShader(Lnet/minecraft/resources/Identifier;)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;"),
        index = 0, remap = false)
    private Identifier sofron$vertexShader(Identifier original) { return SOFRON_SHADER; }

    @ModifyArg(method = {"createShader", "createOITShader"},
        at = @At(value = "INVOKE", target =
            "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;withFragmentShader(Lnet/minecraft/resources/Identifier;)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;"),
        index = 0, remap = false)
    private Identifier sofron$fragmentShader(Identifier original) { return SOFRON_SHADER; }
}
