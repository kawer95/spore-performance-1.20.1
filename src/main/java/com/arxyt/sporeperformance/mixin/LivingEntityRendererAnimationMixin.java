package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.client.render.AnimationLodController;
import com.arxyt.sporeperformance.client.render.LayerAnimationDeduplicator;
import com.arxyt.sporeperformance.client.render.ClientRenderMetrics;
import com.arxyt.sporeperformance.client.render.SporeRenderClassifier;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Intercepts only the model animation call; entity interpolation and rendering remain per frame. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererAnimationMixin {
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Lnet/minecraft/world/entity/Entity;FFFFF)V"))
    private void sporePerformance$applyAnimationLod(EntityModel<Entity> model, Entity entity,
                                                     float limbSwing, float limbAmount, float age,
                                                     float yaw, float pitch) {
        if (!AnimationLodController.restoreIfScheduled(model, entity)) {
            model.setupAnim(entity, limbSwing, limbAmount, age, yaw, pitch);
            AnimationLodController.captureAfterSetup(model, entity);
            if (SporeRenderClassifier.isSporeEntity(entity) && SporeRenderClassifier.isSporeModel(model)) {
                ClientRenderMetrics.increment("animation.setup_computed");
            }
        }
        LayerAnimationDeduplicator.record(model, entity, limbSwing, limbAmount, age, yaw, pitch);
    }
}
