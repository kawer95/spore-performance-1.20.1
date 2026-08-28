package com.arxyt.sporeperformance.world;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** UUID-staggered target acquisition without delaying damage response or an existing target. */
public final class TargetAcquisitionController {
    private static final Map<UUID, Long> LAST_ATTEMPTS = new HashMap<>();

    public static boolean shouldRun(Mob mob) {
        boolean refactorBalanced = PerformanceConfig.REFACTOR_AI_ENABLED.get()
                && PerformanceConfig.REFACTOR_SHARED_PERCEPTION.get();
        if ((!refactorBalanced && !PerformanceConfig.AGGRESSIVE_BALANCED_TARGETING.get())
                || !(mob.level() instanceof ServerLevel level) || !isSpore(mob)) return true;
        // An active target and a damage event are event-driven wakeups.  Do not
        // ask every target goal to rediscover the same target each tick.
        if (mob.hurtTime > 0) return true;
        if (mob.getTarget() != null && mob.getTarget().isAlive()) return false;
        long now = level.getGameTime();
        boolean near = LivingEntitySpatialIndex.INSTANCE.hasPlayerWithin(level, mob,
                PerformanceConfig.AGGRESSIVE_TARGET_NEAR_DISTANCE.get());
        int interval = near ? PerformanceConfig.AGGRESSIVE_TARGET_NEAR_INTERVAL.get()
                : PerformanceConfig.AGGRESSIVE_TARGET_FAR_INTERVAL.get();
        if (refactorBalanced) {
            // The refactored perception frame makes the same 2/5 Tick
            // cadence cheap while preserving prompt acquisition near players.
            interval = Math.max(near ? 2 : 5, interval);
        }
        long previous = LAST_ATTEMPTS.getOrDefault(mob.getUUID(), Long.MIN_VALUE / 2);
        if (now - previous < interval || Math.floorMod(now + mob.getUUID().hashCode(), interval) != 0) {
            PerformanceMetrics.increment("ai.target_attempt_suppressed");
            return false;
        }
        LAST_ATTEMPTS.put(mob.getUUID(), now);
        return true;
    }

    public static boolean isSpore(Mob mob) {
        return com.arxyt.sporeperformance.ai.FungalAiRuntime.isSpore(mob);
    }

    public static void forget(UUID id) { LAST_ATTEMPTS.remove(id); }
    public static void clear() { LAST_ATTEMPTS.clear(); }
    private TargetAcquisitionController() {}
}
