package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.AI.AOEMeleeAttackGoal;
import com.Harbinger.Spore.Sentities.Calamities.Stahlmorder;
import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import com.arxyt.sporeperformance.ai.StahlAiControl;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.DebugTrace;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.function.Predicate;

@Mixin(value = Stahlmorder.StahlMeleeAttackGoal.class, remap = false, priority = 900)
abstract class AiFixStahlMeleeGoalMixin extends AOEMeleeAttackGoal {
    @Shadow public int attackWindup;
    @Shadow public LivingEntity delayedTarget;

    protected AiFixStahlMeleeGoalMixin(PathfinderMob mob, double speed, boolean followUnseen, double hitbox,
                                      float range, Predicate<LivingEntity> targets) {
        super(mob, speed, followUnseen, hitbox, range, targets);
    }

    /** Keep an active Stahl attack from ending merely because navigation briefly reports done. */
    @Overwrite
    public boolean m_8045_() {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get()) return attackWindup > 0 || super.canContinueToUse();
        if (attackWindup > 0) return true;
        LivingEntity target = mob.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (target instanceof Player player && (player.isSpectator() || player.isCreative())) return false;
        return mob.isWithinRestriction(target.blockPosition()) && mob.distanceToSqr(target) <= 9216.0D;
    }

    @Overwrite
    protected void resetAttackCooldown() {
        ticksUntilNextAttack = adjustedTickDelay(PerformanceConfig.REFACTOR_AI_ENABLED.get() ? 30 : 40);
    }

    @Overwrite
    protected void checkAndPerformAttack(LivingEntity living, double distanceSqr) {
        double reachSqr = getAttackReachSqr(living);
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get()) {
            if (mob instanceof Stahlmorder stahl && stahl instanceof StahlAiControl control
                    && ticksUntilNextAttack == 20 && distanceSqr <= reachSqr) {
                stahl.animationOffset = 20;
                stahl.level().broadcastEntityEvent(stahl, (byte) 4);
                stahl.triggerAnimation(control.sporeperformance$decideAnimation(living));
                control.sporeperformance$applyAttackEffect(living, stahl.getMeleeState().getValue());
            }
            if (distanceSqr <= reachSqr && ticksUntilNextAttack <= 0 && mob.hasLineOfSight(living)) {
                resetAttackCooldown();
                if (mob instanceof Stahlmorder stahl) startDelayedAttack(living, stahl);
            }
            return;
        }
        if (distanceSqr <= reachSqr && ticksUntilNextAttack <= 0 && mob.hasLineOfSight(living)) {
            resetAttackCooldown();
            if (mob instanceof Stahlmorder stahl) startDelayedAttack(living, stahl);
        } else if (DebugTrace.enabled(DebugTrace.Category.COMBAT) && mob.level() instanceof ServerLevel level) {
            DebugTrace.event(DebugTrace.Category.COMBAT, level, DebugTrace.trace(mob), mob,
                    "stahl_attack_not_ready", "target=" + living.getUUID() + ",distanceSqr=" + distanceSqr
                            + ",reachSqr=" + reachSqr + ",cooldown=" + ticksUntilNextAttack);
        }
    }

    @Overwrite
    public void startDelayedAttack(LivingEntity target, Stahlmorder stahl) {
        attackWindup = PerformanceConfig.REFACTOR_AI_ENABLED.get() ? 9 : 15;
        delayedTarget = target;
        stahl.animationOffset = 20;
        stahl.level().broadcastEntityEvent(stahl, (byte) 4);
        if (stahl instanceof StahlAiControl control) {
            stahl.triggerAnimation(control.sporeperformance$decideAnimation(target));
        }
        if (DebugTrace.enabled(DebugTrace.Category.COMBAT) && stahl.level() instanceof ServerLevel level)
            DebugTrace.event(DebugTrace.Category.COMBAT, level, DebugTrace.trace(stahl), stahl,
                    "stahl_attack_windup", "target=" + target.getUUID() + ",windup=" + attackWindup
                            + ",state=" + stahl.getMeleeState().getValue());
    }

    @Overwrite
    public void m_8037_() {
        super.tick();
        if (attackWindup <= 0) return;
        --attackWindup;
        if (attackWindup == 1 && delayedTarget != null && delayedTarget.isAlive()) {
            sporeperformance$performDelayedAttack(delayedTarget);
            delayedTarget = null;
        }
    }

    private void sporeperformance$performDelayedAttack(LivingEntity primary) {
        if (!mob.hasLineOfSight(primary)) {
            if (DebugTrace.enabled(DebugTrace.Category.COMBAT) && mob.level() instanceof ServerLevel level)
                DebugTrace.event(DebugTrace.Category.COMBAT, level, DebugTrace.trace(mob), mob,
                        "stahl_hit_rejected_los", "target=" + primary.getUUID());
            return;
        }
        double grace = PerformanceConfig.REFACTOR_AI_ENABLED.get() ? 1.35D : 1.0D;
        if (mob.distanceToSqr(primary) > getAttackReachSqr(primary) * grace) {
            if (DebugTrace.enabled(DebugTrace.Category.COMBAT) && mob.level() instanceof ServerLevel level)
                DebugTrace.event(DebugTrace.Category.COMBAT, level, DebugTrace.trace(mob), mob,
                        "stahl_hit_rejected_range", "target=" + primary.getUUID());
            return;
        }
        mob.swing(InteractionHand.MAIN_HAND);
        mob.doHurtTarget(primary);

        AABB bounds = primary.getBoundingBox().inflate(box);
        Predicate<LivingEntity> filter = PerformanceConfig.REFACTOR_AI_ENABLED.get()
                ? victims.and(EntitySelector.NO_CREATIVE_OR_SPECTATOR) : victims;
        List<LivingEntity> targets;
        if (PerformanceConfig.REFACTOR_AI_ENABLED.get() && PerformanceConfig.REFACTOR_SHARED_PERCEPTION.get()
                && mob.level() instanceof ServerLevel serverLevel) {
            targets = FungalAiRuntime.query(serverLevel, mob, bounds, LivingEntity.class).stream().filter(filter).toList();
        } else {
            targets = mob.level().getEntitiesOfClass(LivingEntity.class, bounds, filter);
        }
        for (LivingEntity target : targets) {
            if (!PerformanceConfig.REFACTOR_AI_ENABLED.get() || target != primary) mob.doHurtTarget(target);
        }
        if (DebugTrace.enabled(DebugTrace.Category.COMBAT) && mob.level() instanceof ServerLevel level)
            DebugTrace.event(DebugTrace.Category.COMBAT, level, DebugTrace.trace(mob), mob,
                    "stahl_hit_committed", "primary=" + primary.getUUID() + ",aoeCandidates=" + targets.size());
    }
}
