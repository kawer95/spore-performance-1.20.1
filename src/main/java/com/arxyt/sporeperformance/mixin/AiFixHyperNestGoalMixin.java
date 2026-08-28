package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.BaseEntities.Hyper;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "com.Harbinger.Spore.Sentities.BaseEntities.Hyper$GoBackToTheNest", remap = false, priority = 900)
abstract class AiFixHyperNestGoalMixin {
    @Shadow protected Hyper hyper;

    @Inject(method = "m_8036_", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$preserveOrdersOnStart(CallbackInfoReturnable<Boolean> callback) {
        if (PerformanceConfig.REFACTOR_AI_ENABLED.get()
                && (hyper.getSearchPos() != null || !hyper.getNavigation().isDone())) callback.setReturnValue(false);
    }

    @Inject(method = "m_8045_", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$yieldToSearchOrder(CallbackInfoReturnable<Boolean> callback) {
        if (PerformanceConfig.REFACTOR_AI_ENABLED.get() && hyper.getSearchPos() != null) callback.setReturnValue(false);
    }
}
