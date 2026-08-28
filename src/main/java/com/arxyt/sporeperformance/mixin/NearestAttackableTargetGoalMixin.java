package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import com.arxyt.sporeperformance.diagnostics.DebugTrace;
import com.arxyt.sporeperformance.world.LivingEntitySpatialIndex;
import com.arxyt.sporeperformance.world.TargetAcquisitionController;
import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import com.arxyt.sporeperformance.ai.SporeTickContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NearestAttackableTargetGoal.class)
abstract class NearestAttackableTargetGoalMixin<T extends LivingEntity> extends TargetGoal {
    @Shadow @Final protected Class<T> targetType;
    @Shadow protected LivingEntity target;
    @Shadow protected TargetingConditions targetConditions;

    protected NearestAttackableTargetGoalMixin(Mob mob, boolean mustSee) { super(mob, mustSee); }

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$staggerTargetSearch(CallbackInfoReturnable<Boolean> callback) {
        if (!TargetAcquisitionController.shouldRun(mob)) {
            if (DebugTrace.enabled(DebugTrace.Category.GOAL) && mob.level() instanceof ServerLevel level)
                DebugTrace.event(DebugTrace.Category.GOAL, level, DebugTrace.trace(mob), mob,
                        "target_goal_deferred", "goal=" + getClass().getName());
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "findTarget", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$useSpatialCandidates(CallbackInfo callback) {
        boolean refactor = PerformanceConfig.REFACTOR_AI_ENABLED.get() && PerformanceConfig.REFACTOR_SHARED_PERCEPTION.get();
        if ((!refactor && !PerformanceConfig.AGGRESSIVE_BALANCED_TARGETING.get()) || !TargetAcquisitionController.isSpore(mob)
                || !(mob.level() instanceof ServerLevel level)) return;
        double range = getFollowDistance();
        AABB area = mob.getBoundingBox().inflate(range, 4.0D, range);
        if (refactor) {
            LivingEntity selected = FungalAiRuntime.INSTANCE.get(level).perception.nearest(mob, area, targetType, targetConditions);
            if (PerformanceConfig.DIAGNOSTICS_AI_SHADOW.get()) {
                LivingEntity nativeNearest = null;
                double nativeDistance = Double.MAX_VALUE;
                java.util.List<T> nativeCandidates = SporeTickContext.withoutRouting(() ->
                        level.getEntitiesOfClass(targetType, area, candidate -> targetConditions.test(mob, candidate)));
                for (T candidate : nativeCandidates) {
                    double distance = mob.distanceToSqr(candidate);
                    if (distance < nativeDistance) { nativeDistance = distance; nativeNearest = candidate; }
                }
                PerformanceMetrics.increment(nativeNearest == selected ? "ai_refactor.shadow.target_match" : "ai_refactor.shadow.target_mismatch");
            }
            target = selected;
        }
        else {
            LivingEntity nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (LivingEntity candidate : LivingEntitySpatialIndex.INSTANCE.query(level, area, targetType, mob)) {
                if (!targetConditions.test(mob, candidate)) continue;
                double distance = mob.distanceToSqr(candidate);
                if (distance < nearestDistance) { nearestDistance = distance; nearest = candidate; }
            }
            target = nearest;
        }
        PerformanceMetrics.increment("ai.vanilla_world_target_query_avoided");
        PerformanceMetrics.increment("ai_refactor.target.searches");
        if (DebugTrace.enabled(DebugTrace.Category.PERCEPTION))
            DebugTrace.event(DebugTrace.Category.PERCEPTION, level, DebugTrace.trace(mob), mob,
                    "target_search_complete", "targetType=" + targetType.getName() + ",selected="
                            + (target == null ? "" : target.getUUID()));
        callback.cancel();
    }
}
