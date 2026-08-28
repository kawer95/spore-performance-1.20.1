package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.AI.LocHiv.FollowOthersGoal;
import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.Harbinger.Spore.Sentities.EvolvingInfected;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import com.arxyt.sporeperformance.world.FollowPartnerSnapshot;
import com.arxyt.sporeperformance.world.FollowPathThrottle;
import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import net.minecraft.server.level.ServerLevel;
import com.google.common.base.Predicate;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.objectweb.asm.Opcodes;

import java.util.List;

/** Avoids the registered Calamity-partner search on infections that can never match its predicate. */
@Mixin(value = FollowOthersGoal.class, remap = false)
abstract class FollowOthersGoalMixin {
    @Shadow @Final private Infected infected;
    @Shadow @Final private Class<? extends LivingEntity> desiredPartner;
    @Shadow @Final private Predicate<LivingEntity> partnerTargeting;
    @Shadow private int searchCooldown;
    @Unique private FollowPathThrottle sporeperformance$pathThrottle;

    @Inject(method = "m_8036_", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$skipImpossibleCalamitySearch(CallbackInfoReturnable<Boolean> callback) {
        if (PerformanceConfig.SAFE_SKIP_NON_EVOLVING_CALAMITY_FOLLOW.get()
                && desiredPartner == Calamity.class && !(infected instanceof EvolvingInfected)) {
            PerformanceMetrics.increment("ai.impossible_calamity_follow_skipped");
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "findNearestPartner", at = @At("HEAD"), cancellable = true, require = 0)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void sporeperformance$groupPartnerLookup(CallbackInfoReturnable<LivingEntity> callback) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get() || !PerformanceConfig.REFACTOR_GROUP_COORDINATION.get()
                || !(infected.level() instanceof ServerLevel level)) return;
        LivingEntity partner = FungalAiRuntime.INSTANCE.get(level).groups.nearestPartner(infected,
                (Class) desiredPartner, partnerTargeting, 32.0D);
        callback.setReturnValue(partner);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @org.spongepowered.asm.mixin.injection.Redirect(method = "findNearestPartner", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", remap = false), require = 0)
    private List sporeperformance$snapshotPartnerCandidates(Level level, Class type, AABB bounds, java.util.function.Predicate nativePredicate) {
        return FollowPartnerSnapshot.query(infected, level, type, bounds, partnerTargeting);
    }

    /**
     * Staggers only future partner re-checks. The first native check still runs immediately;
     * this gives the shared snapshot an actual UUID phase instead of a cosmetic metric.
     */
    @org.spongepowered.asm.mixin.injection.Redirect(method = "m_8036_", at = @At(value = "FIELD", target = "Lcom/Harbinger/Spore/Sentities/AI/LocHiv/FollowOthersGoal;searchCooldown:I", opcode = Opcodes.PUTFIELD, remap = false), require = 0)
    private void sporeperformance$staggerSearchCooldown(FollowOthersGoal goal, int nativeCooldown) {
        if (PerformanceConfig.AGGRESSIVE_GROUP_SENSING.get()) {
            long now = infected.level().getGameTime();
            int phaseDelay = (int) Math.floorMod((long) infected.getUUID().hashCode() - now, 20L);
            searchCooldown = nativeCooldown + phaseDelay;
        } else searchCooldown = nativeCooldown;
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "m_8037_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;m_5624_(Lnet/minecraft/world/entity/Entity;D)Z", remap = false), require = 1)
    private boolean sporeperformance$reusePartnerPath(PathNavigation navigation, Entity target, double speed) {
        if (PerformanceConfig.REFACTOR_AI_ENABLED.get() && PerformanceConfig.REFACTOR_FOLLOW_GROUP_PATHING.get()
                && target instanceof LivingEntity partner) {
            FungalAiRuntime.LevelRuntime runtime = infected.level() instanceof ServerLevel level
                    ? FungalAiRuntime.INSTANCE.get(level) : null;
            if (runtime != null && runtime.groups.tryDirectFollow(navigation, partner, speed)) return true;
            if (runtime != null) {
                net.minecraft.core.BlockPos waypoint = runtime.groups.sharedWaypoint(infected, partner);
                if (waypoint != null) {
                    boolean result = navigation.moveTo(waypoint.getX() + 0.5D, waypoint.getY(), waypoint.getZ() + 0.5D, speed);
                    PerformanceMetrics.increment(result ? "ai.follow.corridor_path_created" : "ai.follow.corridor_path_failed");
                    return result;
                }
            }
        }
        boolean reuse = PerformanceConfig.AGGRESSIVE_FOLLOW_PATH_REUSE.get();
        boolean backoff = PerformanceConfig.AGGRESSIVE_FOLLOW_PATH_BACKOFF.get();
        if ((!reuse && !backoff) || !(target instanceof LivingEntity partner)) return navigation.moveTo(target, speed);

        FollowPathThrottle throttle = sporeperformance$pathThrottle();
        long now = infected.level().getGameTime();
        int interval = PerformanceConfig.AGGRESSIVE_FOLLOW_REPATH_INTERVAL.get();
        double threshold = PerformanceConfig.AGGRESSIVE_FOLLOW_MOVE_THRESHOLD.get();
        if (!throttle.shouldAttempt(partner.getUUID(), partner.getX(), partner.getY(), partner.getZ(), now,
                !navigation.isDone(), reuse, interval, threshold, backoff)) {
            PerformanceMetrics.increment("ai.follow_path_reused_or_deferred");
            return true;
        }

        boolean success = navigation.moveTo(target, speed);
        throttle.recordAttempt(partner.getUUID(), partner.getX(), partner.getY(), partner.getZ(), now, success,
                interval, FollowPathThrottle.phase(infected.getUUID()), backoff, PerformanceConfig.AGGRESSIVE_FOLLOW_BACKOFF_MAX.get());
        PerformanceMetrics.increment(success ? "ai.follow_path_created" : "ai.follow_path_failed");
        return success;
    }

    @Inject(method = {"m_8056_", "m_8041_"}, at = @At("HEAD"), require = 0)
    private void sporeperformance$resetPartnerPathState(CallbackInfo callback) {
        if (sporeperformance$pathThrottle != null) sporeperformance$pathThrottle.reset();
    }

    @Unique
    private FollowPathThrottle sporeperformance$pathThrottle() {
        if (sporeperformance$pathThrottle == null) sporeperformance$pathThrottle = new FollowPathThrottle();
        return sporeperformance$pathThrottle;
    }
}
