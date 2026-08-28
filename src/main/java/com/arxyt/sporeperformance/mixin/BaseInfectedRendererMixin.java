package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Client.Special.BaseInfectedRenderer;
import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.arxyt.sporeperformance.client.render.IllusionRenderOptimizer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Avoids constructing an unused illusion proxy on every ordinary infected render. */
@Mixin(value = BaseInfectedRenderer.class, remap = false)
public abstract class BaseInfectedRendererMixin {
    @Redirect(method = "m_7392_(Lnet/minecraft/world/entity/Mob;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lcom/Harbinger/Spore/Client/Special/BaseInfectedRenderer;getForm(Lcom/Harbinger/Spore/Sentities/BaseEntities/Infected;)Lnet/minecraft/world/entity/Entity;",
                    remap = false), remap = false)
    private Entity sporePerformance$deferIllusionForm(BaseInfectedRenderer<?, ?> renderer, Infected infected) {
        return IllusionRenderOptimizer.createFormForRender(renderer, infected);
    }
}
