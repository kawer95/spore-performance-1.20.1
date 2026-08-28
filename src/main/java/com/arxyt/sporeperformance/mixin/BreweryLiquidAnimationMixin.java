package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Client.Layers.BreweryLiquid;
import com.Harbinger.Spore.Client.Models.BraureiModel;
import com.Harbinger.Spore.Sentities.Organoids.Brauerei;
import com.arxyt.sporeperformance.client.render.ClientRenderMetrics;
import com.arxyt.sporeperformance.client.render.LayerAnimationDeduplicator;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = BreweryLiquid.class, remap = false)
public abstract class BreweryLiquidAnimationMixin {
    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/Harbinger/Spore/Sentities/Organoids/Brauerei;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lcom/Harbinger/Spore/Client/Models/BraureiModel;setupAnim(Lcom/Harbinger/Spore/Sentities/Organoids/Brauerei;FFFFF)V", remap = false), remap = false)
    private void sporePerformance$skipDuplicateSetup(BraureiModel<Brauerei> model, Brauerei entity,
                                                      float limbSwing, float limbAmount, float age,
                                                      float yaw, float pitch) {
        if (PerformanceConfig.CLIENT_SKIP_DUPLICATE_LAYER_ANIMATION.get()
                && LayerAnimationDeduplicator.isExactDuplicate(model, entity, limbSwing, limbAmount, age, yaw, pitch)) {
            ClientRenderMetrics.increment("animation.duplicate_layer_setup_skipped");
            return;
        }
        model.setupAnim(entity, limbSwing, limbAmount, age, yaw, pitch);
    }
}
