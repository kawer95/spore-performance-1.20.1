package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Client.Layers.TranslucentLayer;
import com.arxyt.sporeperformance.client.render.AcceleratedRenderingBridge;
import com.arxyt.sporeperformance.client.render.EffectLayerPolicy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TranslucentLayer.class, remap = false)
public abstract class TranslucentLayerRenderMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void sporePerformance$cull(PoseStack stack, MultiBufferSource source, int light, LivingEntity entity,
                                       float a, float b, float c, float d, float e, float f, CallbackInfo callback) {
        if (EffectLayerPolicy.shouldCull(entity, EffectLayerPolicy.Kind.TRANSLUCENT)) callback.cancel();
    }

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = true), remap = false)
    private VertexConsumer sporePerformance$accelerate(MultiBufferSource source, RenderType type) {
        return AcceleratedRenderingBridge.getBuffer(source, type, false);
    }
}
