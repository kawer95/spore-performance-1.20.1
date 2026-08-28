package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Client.Layers.DrakeMembraneLayer;
import com.Harbinger.Spore.Sentities.Calamities.Verfalldrachen;
import com.arxyt.sporeperformance.client.render.AcceleratedRenderingBridge;
import com.arxyt.sporeperformance.client.render.EffectLayerPolicy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DrakeMembraneLayer.class, remap = false)
public abstract class DrakeMembraneLayerRenderMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/Harbinger/Spore/Sentities/Calamities/Verfalldrachen;FFFFFF)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void sporePerformance$cull(PoseStack stack, MultiBufferSource source, int light, Verfalldrachen entity,
                                       float a, float b, float c, float d, float e, float f, CallbackInfo callback) {
        if (EffectLayerPolicy.shouldCull(entity, EffectLayerPolicy.Kind.TRANSLUCENT)) callback.cancel();
    }

    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/Harbinger/Spore/Sentities/Calamities/Verfalldrachen;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;", remap = true), remap = false)
    private VertexConsumer sporePerformance$accelerate(MultiBufferSource source, RenderType type) {
        return AcceleratedRenderingBridge.getBuffer(source, type, false);
    }
}
