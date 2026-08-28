package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Client.Layers.BreweryLiquid;
import com.Harbinger.Spore.Sentities.Organoids.Brauerei;
import com.arxyt.sporeperformance.client.render.EffectLayerPolicy;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BreweryLiquid.class, remap = false)
public abstract class BreweryLiquidRenderMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/Harbinger/Spore/Sentities/Organoids/Brauerei;FFFFFF)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void sporePerformance$cull(PoseStack stack, MultiBufferSource source, int light, Brauerei entity,
                                       float a, float b, float c, float d, float e, float f, CallbackInfo callback) {
        if (EffectLayerPolicy.shouldCull(entity, EffectLayerPolicy.Kind.TRANSLUCENT)) callback.cancel();
    }
}
