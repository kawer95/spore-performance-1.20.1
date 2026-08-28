package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.ai.BusserVariantGoalRuntime;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps variant pruning at the WrappedGoal boundary, before expensive custom canUse logic. */
@Mixin(WrappedGoal.class)
abstract class WrappedGoalVariantGateMixin {
    @Shadow public abstract Goal getGoal();

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$skipIrrelevantVariantGoal(CallbackInfoReturnable<Boolean> callback) {
        if (BusserVariantGoalRuntime.skip(getGoal())) callback.setReturnValue(false);
    }
}
