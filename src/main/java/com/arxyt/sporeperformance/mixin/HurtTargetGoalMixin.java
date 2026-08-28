package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.AI.HurtTargetGoal;
import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HurtTargetGoal.class, remap = false)
abstract class HurtTargetGoalMixin extends TargetGoal {
    @Shadow private Class<?>[] toIgnoreAlert;
    protected HurtTargetGoalMixin(Mob mob, boolean mustSee) { super(mob, mustSee); }

    @Inject(method = "alertOthers", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$broadcastThreatOnce(CallbackInfo callback) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get() || !PerformanceConfig.REFACTOR_EVENT_THREATS.get()
                || !(mob.level() instanceof ServerLevel level)) return;
        FungalAiRuntime.INSTANCE.get(level).groups.propagateHurt(mob, mob.getLastHurtByMob(), toIgnoreAlert);
        callback.cancel();
    }
}
