package com.arxyt.sporeperformance.world;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.world.entity.Mob;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Defers only inactive selector evaluation for idle Basic/Evolved/Hyper infected.
 * Running goals continue through {@code tickRunningGoals(true)} on every vanilla full-selector
 * slot, while combat, damage and target invalidation always use the original selector path.
 */
public final class InfectedSelectorScheduler {
    private static final Map<Mob, State> STATES = new WeakHashMap<>();

    public static boolean deferFullTick(Mob mob) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get()
                || !PerformanceConfig.REFACTOR_TICK_PIPELINE.get()
                || !PerformanceConfig.REFACTOR_IDLE_SELECTOR_STAGGER.get()
                || mob.level().isClientSide || !RemoteIdleAiController.isManagedFamily(mob)) return false;
        if (mob.getTarget() != null || mob.hurtTime > 0 || mob.isPassenger()) {
            synchronized (STATES) { STATES.remove(mob); }
            return false;
        }
        long now = mob.level().getGameTime();
        synchronized (STATES) {
            State state = STATES.computeIfAbsent(mob, ignored -> new State());
            if (state.evaluatedTick == now) return state.defer;
            state.evaluatedTick = now;
            if (state.nextFullTick == Long.MIN_VALUE || now >= state.nextFullTick) {
                state.nextFullTick = now + PerformanceConfig.REFACTOR_IDLE_SELECTOR_INTERVAL.get();
                state.defer = false;
                PerformanceMetrics.increment("ai_refactor.selector.idle_full_ticks");
            } else {
                state.defer = true;
                PerformanceMetrics.increment("ai_refactor.selector.idle_full_ticks_deferred");
            }
            return state.defer;
        }
    }

    public static void clear() {
        synchronized (STATES) { STATES.clear(); }
    }

    private static final class State {
        private long evaluatedTick = Long.MIN_VALUE;
        private long nextFullTick = Long.MIN_VALUE;
        private boolean defer;
    }

    private InfectedSelectorScheduler() {}
}
