package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.Calamities.Hinderburg;
import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces AI Fix's stream/world scan with the shared loaded-entity index. */
@Mixin(value = Hinderburg.class, remap = false, priority = 1100)
abstract class AiFixHinderburgTargetMixin {
    @Inject(method = "m_8119_", at = @At("HEAD"))
    private void sporeperformance$indexedGroundTarget(CallbackInfo callback) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get() || !PerformanceConfig.REFACTOR_SHARED_PERCEPTION.get()) return;
        Hinderburg self = (Hinderburg) (Object) this;
        LivingEntity current = self.getTarget();
        if (current == null || current.onGround() || current.isInFluidType()
                || !(self.level() instanceof ServerLevel level)) return;
        double range = self.getAttributeValue(Attributes.FOLLOW_RANGE);
        LivingEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : FungalAiRuntime.query(level, self, self.getBoundingBox().inflate(range), LivingEntity.class)) {
            if (candidate == current || !(candidate.onGround() || candidate.isInFluidType())
                    || !self.canAttack(candidate) || !(candidate instanceof Player || self.TARGET_SELECTOR.test(candidate))) continue;
            double distance = self.distanceToSqr(candidate);
            if (distance < bestDistance || distance == bestDistance && best != null && candidate.getId() < best.getId()) {
                best = candidate;
                bestDistance = distance;
            }
        }
        self.setTarget(best);
    }
}
