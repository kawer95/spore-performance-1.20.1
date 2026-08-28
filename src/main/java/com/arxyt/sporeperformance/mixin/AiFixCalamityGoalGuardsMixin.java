package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.AI.AOEMeleeAttackGoal;
import com.Harbinger.Spore.Sentities.Calamities.Hinderburg;
import com.Harbinger.Spore.Sentities.Calamities.Howitzer;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.world.entity.PathfinderMob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents mutually exclusive melee goals from acquiring MOVE/LOOK and pathfinding every tick. */
@Mixin(value = AOEMeleeAttackGoal.class, remap = false, priority = 900)
abstract class AiFixCalamityGoalGuardsMixin {
    @Shadow @Final protected PathfinderMob mob;

    @Inject(method = {"m_8036_", "m_8045_"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void sporeperformance$guardArtilleryMelee(CallbackInfoReturnable<Boolean> callback) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get()) return;
        if (mob instanceof Hinderburg) callback.setReturnValue(false);
        else if (mob instanceof Howitzer howitzer && !howitzer.isInMeleeRange()) callback.setReturnValue(false);
    }
}
