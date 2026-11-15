package pl.sofron.light.mixin;

import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.minecraft.resources.Identifier;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.IdentityHashMap;
import java.util.Map;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.minecraft.client.renderer.oit.OitStage;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.sofron.light.SofronPackLightClient;
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

    @Unique private final Map<TerrainRenderPass, RenderPipeline> sofron$stationary = new IdentityHashMap<>();
    @Unique private boolean sofron$buildingStationary;
    @Shadow private RenderPipeline createShader(String name, TerrainRenderPass pass) { throw new AssertionError(); }

    @Inject(method = "compileProgram", at = @At("HEAD"), cancellable = true)
    private void sofron$stationaryPipeline(TerrainRenderPass pass, OitStage stage,
            CallbackInfoReturnable<RenderPipeline> result) {
        if (stage != null || SofronPackLightClient.VOXELS.field() != null
                || SofronPackLightClient.CONFIG == null || !"off".equals(SofronPackLightClient.CONFIG.rgbDebugMode)) return;
        var program = sofron$stationary.get(pass);
        if (program == null) {
            sofron$buildingStationary = true;
            try {
                program = createShader("blocks/block_layer_opaque", pass);
                sofron$stationary.put(pass, program);
            } finally {
                sofron$buildingStationary = false;
            }
        }
        result.setReturnValue(program);
    }

    @Redirect(method = "createShader", at = @At(value = "INVOKE", target =
        "Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;withLocation(Lnet/minecraft/resources/Identifier;)Lcom/mojang/renderpearl/api/pipeline/RenderPipeline$Builder;"))
    private RenderPipeline.Builder sofron$stationaryDefine(RenderPipeline.Builder builder, Identifier location) {
        if (sofron$buildingStationary) {
            builder.withShaderDefine("SOFRON_STATIONARY_ONLY");
            location = location.withPath(location.getPath() + "_sofron_stationary");
        }
        return builder.withLocation(location);
    }

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
