package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Client.Layers.DrakeMembraneLayer;
import com.arxyt.sporeperformance.client.render.ClientRenderMetrics;
import com.arxyt.sporeperformance.client.render.LayerAnimationDeduplicator;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = DrakeMembraneLayer.class, remap = false)
public abstract class DrakeMembraneLayerAnimationMixin {
    @Redirect(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILcom/Harbinger/Spore/Sentities/Calamities/Verfalldrachen;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V", remap = true), remap = false)
    private void sporePerformance$skipDuplicateSetup(EntityModel<Entity> model, Entity entity,
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
