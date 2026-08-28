package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.AI.LeapGoal;
import com.Harbinger.Spore.Sentities.Calamities.Howitzer;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LeapGoal.class, remap = false, priority = 900)
abstract class AiFixLeapGoalMixin {
    @Shadow @Final protected Mob mob;

    @Inject(method = {"m_8036_", "m_8045_"}, at = @At("HEAD"), cancellable = true)
    private void sporeperformance$onlyLeapInMelee(CallbackInfoReturnable<Boolean> callback) {
        if (PerformanceConfig.REFACTOR_AI_ENABLED.get() && mob instanceof Howitzer howitzer
                && !howitzer.isInMeleeRange()) callback.setReturnValue(false);
    }
}
