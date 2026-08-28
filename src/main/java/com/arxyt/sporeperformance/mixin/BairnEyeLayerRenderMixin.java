package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Client.Layers.BairnEyeLayer;
import com.Harbinger.Spore.Sentities.BasicInfected.Bairn;
import com.arxyt.sporeperformance.client.render.AcceleratedRenderingBridge;
import com.arxyt.sporeperformance.client.render.EffectLayerPolicy;
import com.arxyt.sporeperformance.client.render.OpaqueModelPartRenderer;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BairnEyeLayer.class, remap = false)
public abstract class BairnEyeLayerRenderMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/Harbinger/Spore/Sentities/BasicInfected/Bairn;FFFFFF)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void sporePerformance$cull(PoseStack stack, MultiBufferSource source, int light, Bairn entity,
                                       float a, float b, float c, float d, float e, float f, CallbackInfo callback) {
        if (EffectLayerPolicy.shouldCull(entity, EffectLayerPolicy.Kind.EYE)) {
            callback.cancel();
            return;
        }
        if (!PerformanceConfig.CLIENT_EYE_OPAQUE_PART_MASK.get()) return;
        ResourceLocation texture = BairnEyeLayer.TEXTURE.get(entity.getVariant());
        // Unknown variants are rendered by the original layer instead of constructing a null RenderType.
        if (texture == null) return;
        BairnEyeLayer<?, ?> layer = (BairnEyeLayer<?, ?>) (Object) this;
        VertexConsumer consumer = AcceleratedRenderingBridge.getBuffer(source, RenderType.eyes(texture), true);
        if (OpaqueModelPartRenderer.render(layer.getParentModel(), texture, stack, consumer,
                15728640, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 1, 1, 1, 1)) callback.cancel();
    }

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/Harbinger/Spore/Sentities/BasicInfected/Bairn;FFFFFF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
                    remap = true), remap = false)
    private VertexConsumer sporePerformance$accelerate(MultiBufferSource source, RenderType type) {
        return AcceleratedRenderingBridge.getBuffer(source, type, true);
    }
}
