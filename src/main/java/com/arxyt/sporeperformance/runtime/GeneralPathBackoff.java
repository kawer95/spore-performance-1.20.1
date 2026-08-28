package com.arxyt.sporeperformance.runtime;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import com.arxyt.sporeperformance.world.TargetAcquisitionController;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Successful-path reuse and bounded 20/40/80 tick failure backoff for Spore mobs. */
public final class GeneralPathBackoff {
    private static final Map<UUID, State> STATES = new HashMap<>();

    public static boolean shouldSuppress(Mob mob, Entity target) {
        if (!PerformanceConfig.AGGRESSIVE_GENERAL_PATH_BACKOFF.get() || !TargetAcquisitionController.isSpore(mob)) return false;
        State state = STATES.computeIfAbsent(mob.getUUID(), ignored -> new State());
        long now = mob.level().getGameTime();
        Vec3 position = target.position();
        double threshold = PerformanceConfig.AGGRESSIVE_PATH_TARGET_MOVE_THRESHOLD.get();
        boolean changed = state.targetId == null || !state.targetId.equals(target.getUUID()) || state.targetPosition == null
                || state.targetPosition.distanceToSqr(position) > threshold * threshold || mob.hurtTime > 0;
        if (changed) {
            state.targetId = target.getUUID();
            state.targetPosition = position;
            state.nextAttempt = 0L;
            state.failures = 0;
            return false;
        }
        if (now < state.nextAttempt) {
            PerformanceMetrics.increment("path.general_suppressed");
            return true;
        }
        state.targetPosition = position;
        return false;
    }

    public static void record(Mob mob, Entity target, Path path) {
        if (!PerformanceConfig.AGGRESSIVE_GENERAL_PATH_BACKOFF.get() || !TargetAcquisitionController.isSpore(mob)) return;
        State state = STATES.computeIfAbsent(mob.getUUID(), ignored -> new State());
        long now = mob.level().getGameTime();
        state.targetId = target.getUUID();
        state.targetPosition = target.position();
        if (path != null) {
            state.failures = 0;
            state.nextAttempt = now + PerformanceConfig.AGGRESSIVE_PATH_MIN_INTERVAL.get();
            PerformanceMetrics.increment("path.general_created");
        } else {
            state.failures = Math.min(3, state.failures + 1);
            int delay = Math.min(PerformanceConfig.AGGRESSIVE_PATH_BACKOFF_MAX.get(), 20 << (state.failures - 1));
            state.nextAttempt = now + delay;
            PerformanceMetrics.increment("path.general_failed");
        }
    }

    public static void forget(UUID id) { STATES.remove(id); }
    public static void clear() { STATES.clear(); }
    private static final class State {
        private UUID targetId;
        private Vec3 targetPosition;
        private long nextAttempt;
        private int failures;
    }
    private GeneralPathBackoff() {}
}
