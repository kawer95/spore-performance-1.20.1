package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.AI.LocHiv.SearchAreaGoal;
import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Preserves AI Fix's search-order state machine without adding a polling service. */
@Mixin(value = SearchAreaGoal.class, remap = false, priority = 900)
abstract class AiFixSearchAreaGoalMixin {
    @Shadow @Final public Infected infected;

    @Inject(method = "m_8045_", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$continueOnlyWithOrder(CallbackInfoReturnable<Boolean> callback) {
        if (PerformanceConfig.REFACTOR_AI_ENABLED.get())
            callback.setReturnValue(infected.getSearchPos() != null && infected.getTarget() == null);
    }

    @ModifyConstant(method = "m_8037_", constant = @Constant(doubleValue = 9.0D), require = 0)
    private double sporeperformance$tighterArrival(double original) {
        return PerformanceConfig.REFACTOR_AI_ENABLED.get() ? 3.0D : original;
    }
}
