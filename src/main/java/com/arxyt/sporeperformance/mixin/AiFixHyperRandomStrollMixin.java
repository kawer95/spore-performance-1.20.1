package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.BaseEntities.Hyper;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RandomStrollGoal.class)
abstract class AiFixHyperRandomStrollMixin {
    @Shadow protected PathfinderMob mob;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$preserveHyperPath(CallbackInfoReturnable<Boolean> callback) {
        if (PerformanceConfig.REFACTOR_AI_ENABLED.get() && mob instanceof Hyper
                && !mob.getNavigation().isDone()) callback.setReturnValue(false);
    }
}
