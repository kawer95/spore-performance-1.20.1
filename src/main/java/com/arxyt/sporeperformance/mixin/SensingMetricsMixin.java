package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.sensing.Sensing;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Sensing.class)
abstract class SensingMetricsMixin {
    @Shadow @Final private Mob mob;

    @Inject(method = "hasLineOfSight", at = @At("RETURN"))
    private void sporeperformance$countLos(Entity target, CallbackInfoReturnable<Boolean> callback) {
        if (!PerformanceMetrics.aiEnabled() || !FungalAiRuntime.isSpore(mob)) return;
        PerformanceMetrics.increment("ai_refactor.los.checks");
        PerformanceMetrics.increment(callback.getReturnValue() ? "ai_refactor.los.visible" : "ai_refactor.los.blocked");
    }
}
