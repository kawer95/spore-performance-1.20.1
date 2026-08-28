package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.EvolvedInfected.Busser;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Registers one variant goal set and scopes the cheap variant gate to Busser's AI tick. */
@Mixin(value = Busser.class, remap = false)
abstract class BusserVariantMixin {
    @Shadow public abstract int getTypeVariant();
    @Unique private int sporeperformance$lastVariant = Integer.MIN_VALUE;

    @Redirect(method = "m_7350_", at = @At(value = "INVOKE",
            target = "Lcom/Harbinger/Spore/Sentities/EvolvedInfected/Busser;addVariantGoals()V", remap = false), require = 0)
    private void sporeperformance$addVariantGoalsOnce(Busser busser) {
        if (!PerformanceConfig.REFACTOR_BUSSER_ENABLED.get()
                || !PerformanceConfig.REFACTOR_BUSSER_VARIANT_GOAL_PRUNING.get()) {
            busser.addVariantGoals();
            return;
        }
        int variant = busser.getTypeVariant();
        if (sporeperformance$lastVariant != variant) {
            sporeperformance$lastVariant = variant;
            busser.addVariantGoals();
            PerformanceMetrics.increment("busser.variant_goal_rebuilt");
        } else {
            PerformanceMetrics.increment("busser.variant_goal_rebuild_avoided");
        }
    }
}
