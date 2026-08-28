package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Client.Layers.EyeLayer;
import com.arxyt.sporeperformance.client.render.AcceleratedRenderingBridge;
import com.arxyt.sporeperformance.client.render.EffectLayerPolicy;
import com.arxyt.sporeperformance.client.render.OpaqueModelPartRenderer;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EyeLayer.class, remap = false)
public abstract class EyeLayerRenderMixin {
    @Shadow(remap = false) protected ResourceLocation textureLocation;

    @Inject(method = "m_6494_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void sporePerformance$cull(PoseStack stack, MultiBufferSource source, int light, Entity entity,
                                       float a, float b, float c, float d, float e, float f, CallbackInfo callback) {
        if (EffectLayerPolicy.shouldCull(entity, EffectLayerPolicy.Kind.EYE)) {
            callback.cancel();
            return;
        }
        if (!PerformanceConfig.CLIENT_EYE_OPAQUE_PART_MASK.get()) return;
        // A missing/changed optional layer texture must fall through to Spore's original renderer.
        if (textureLocation == null) return;
        EyeLayer<?, ?> layer = (EyeLayer<?, ?>) (Object) this;
        VertexConsumer consumer = AcceleratedRenderingBridge.getBuffer(source, RenderType.eyes(textureLocation), true);
        if (OpaqueModelPartRenderer.render(layer.getParentModel(), textureLocation, stack, consumer,
                15728640, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 1, 1, 1, 1)) callback.cancel();
    }

    @Redirect(method = "m_6494_(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;",
                    remap = true), remap = false)
    private VertexConsumer sporePerformance$accelerate(MultiBufferSource source, RenderType type) {
        return AcceleratedRenderingBridge.getBuffer(source, type, true);
    }
}
