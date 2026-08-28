package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.Calamities.Stahlmorder;
import com.arxyt.sporeperformance.ai.StahlAiControl;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.DebugTrace;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Stahlmorder.StaLeapGoal.class, remap = false, priority = 900)
abstract class AiFixStahlLeapGuardMixin extends Goal {
    private static final double MIN_DIRECTION_LENGTH_SQR = 1.0E-7D;
    @Shadow @Final private Stahlmorder mob;
    @Shadow private LivingEntity target;

    @Inject(method = "m_8036_", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$usefulLeapDistance(CallbackInfoReturnable<Boolean> callback) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get()) return;
        LivingEntity target = mob.getTarget();
        if (mob.getJumpOffset() > 0 || mob.isInWater() || !mob.onGround() || target == null || !target.isAlive()) {
            if (DebugTrace.enabled(DebugTrace.Category.GOAL) && mob.level() instanceof ServerLevel level)
                DebugTrace.event(DebugTrace.Category.GOAL, level, DebugTrace.trace(mob), mob, "stahl_leap_rejected",
                        "cooldown=" + mob.getJumpOffset() + ",water=" + mob.isInWater() + ",ground=" + mob.onGround()
                                + ",targetValid=" + (target != null && target.isAlive()));
            callback.setReturnValue(false);
            return;
        }
        double distance = mob.distanceToSqr(target);
        if (distance < 144.0D || distance > 1156.0D) {
            if (DebugTrace.enabled(DebugTrace.Category.GOAL) && mob.level() instanceof ServerLevel level)
                DebugTrace.event(DebugTrace.Category.GOAL, level, DebugTrace.trace(mob), mob,
                        "stahl_leap_distance_rejected", "distanceSqr=" + distance);
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "m_8056_", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$startTargetedLeap(CallbackInfo callback) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get()) return;
        if (target == null || !target.isAlive()) {
            callback.cancel();
            return;
        }
        // The control bridge is deliberately optional.  If the outer Stahl
        // mixin was rejected by a signature probe, leave the original goal in
        // charge instead of casting a vanilla Stahlmorder and crashing the
        // server (this is also safe with older add-on jars).
        if (!(mob instanceof StahlAiControl control)) return;

        Vec3 away = new Vec3(mob.getX() - target.getX(), 0.0D, mob.getZ() - target.getZ());
        if (away.lengthSqr() <= MIN_DIRECTION_LENGTH_SQR) away = new Vec3(1.0D, 0.0D, 0.0D);
        double angle = (mob.getRandom().nextDouble() - 0.5D) * Math.PI * 0.55D;
        Vec3 offset = away.normalize().yRot((float) angle).scale(3.0D);
        Vec3 landing = new Vec3(target.getX() + offset.x, target.getY(), target.getZ() + offset.z);
        Vec3 movement = new Vec3(landing.x - mob.getX(), 0.0D, landing.z - mob.getZ());
        if (movement.lengthSqr() > MIN_DIRECTION_LENGTH_SQR) {
            double speed = Mth.clamp(movement.horizontalDistance() * 0.145D, 1.0D, 2.65D);
            movement = movement.normalize().scale(speed);
        }

        mob.getLookControl().setLookAt(target, 10.0F, (float) mob.getMaxHeadXRot());
        Vec3 current = mob.getDeltaMovement();
        mob.setDeltaMovement(current.x * 0.2D + movement.x, current.y + 1.25D, current.z * 0.2D + movement.z);
        control.sporeperformance$beginControlledLeap(landing);
        mob.setJumpOffset(90);
        if (DebugTrace.enabled(DebugTrace.Category.STAHL) && mob.level() instanceof ServerLevel level)
            DebugTrace.event(DebugTrace.Category.STAHL, level, DebugTrace.trace(mob), mob,
                    "leap_started", "target=" + target.getUUID() + ",landing=" + landing + ",velocity=" + mob.getDeltaMovement());
        callback.cancel();
    }
}
