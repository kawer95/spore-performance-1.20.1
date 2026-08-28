package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.AI.CustomMeleeAttackGoal;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CustomMeleeAttackGoal.class, remap = false)
abstract class CustomMeleeAttackGoalMetricsMixin {
    @Inject(method = "m_8036_", at = @At("HEAD"))
    private void sporeperformance$countCanUse(CallbackInfoReturnable<Boolean> callback) {
        PerformanceMetrics.increment("ai_refactor.goal.custom_melee.can_use");
    }
    @Inject(method = "m_8056_", at = @At("HEAD"))
    private void sporeperformance$countStart(CallbackInfo callback) {
        PerformanceMetrics.increment("ai_refactor.goal.custom_melee.start");
    }
    @Inject(method = "m_8041_", at = @At("HEAD"))
    private void sporeperformance$countStop(CallbackInfo callback) {
        PerformanceMetrics.increment("ai_refactor.goal.custom_melee.stop");
    }
    @Inject(method = "m_8037_", at = @At("HEAD"))
    private void sporeperformance$countTick(CallbackInfo callback) {
        PerformanceMetrics.increment("ai_refactor.goal.custom_melee.tick");
    }
}
