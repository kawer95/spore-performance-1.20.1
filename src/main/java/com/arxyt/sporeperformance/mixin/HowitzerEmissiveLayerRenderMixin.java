package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Client.Layers.HowitzerEmissiveLayer;
import com.Harbinger.Spore.Client.Layers.SporeRenderTypes;
import com.Harbinger.Spore.Sentities.Calamities.Howitzer;
import com.arxyt.sporeperformance.client.render.AcceleratedRenderingBridge;
import com.arxyt.sporeperformance.client.render.EffectLayerPolicy;
import com.arxyt.sporeperformance.client.render.OpaqueModelPartRenderer;
import com.arxyt.sporeperformance.client.render.SporeLayerTextures;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HowitzerEmissiveLayer.class, remap = false)
public abstract class HowitzerEmissiveLayerRenderMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/Harbinger/Spore/Sentities/Calamities/Howitzer;FFFFFF)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void sporePerformance$cull(PoseStack stack, MultiBufferSource source, int light, Howitzer entity,
                                       float a, float b, float c, float d, float e, float f, CallbackInfo callback) {
        if (EffectLayerPolicy.shouldCull(entity, EffectLayerPolicy.Kind.EMISSIVE)) {
            callback.cancel();
            return;
        }
        if (!PerformanceConfig.CLIENT_EMISSIVE_OPAQUE_PART_MASK.get() || entity.isInvisible()) return;
        /*
         * Keep constants outside the mixin class. A mixin's static initializer is merged into the
         * target and is not a safe owner for compatibility constants across transformer versions.
         */
        var texture = SporeLayerTextures.howitzer(entity.isRadioactive());
        HowitzerEmissiveLayer<?, ?> layer = (HowitzerEmissiveLayer<?, ?>) (Object) this;
        VertexConsumer consumer = AcceleratedRenderingBridge.getBuffer(source, SporeRenderTypes.glowingTranslucent(texture), true);
        float alpha = 0.5F + 0.5F * Mth.sin(d * 0.01F);
        if (OpaqueModelPartRenderer.render(layer.getParentModel(), texture, stack, consumer,
                light, 15728640, 1, 1, 1, alpha)) callback.cancel();
    }

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/Harbinger/Spore/Sentities/Calamities/Howitzer;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = true), remap = false)
    private VertexConsumer sporePerformance$accelerate(MultiBufferSource source, RenderType type) {
        return AcceleratedRenderingBridge.getBuffer(source, type, true);
    }
}
