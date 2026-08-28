package com.arxyt.sporeperformance.world;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import com.arxyt.sporeperformance.diagnostics.DebugTrace;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Selects bounded active/work sets without deleting saved entities. */
public final class FungalWorkBudget {
    public enum WorkKind { GASTGEBER, MOUND, TENDRIL }
    public static final FungalWorkBudget INSTANCE = new FungalWorkBudget();
    private final Map<ServerLevel, State> levels = new IdentityHashMap<>();

    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob mob) || !isSpore(mob)) return;
        stateFor(level, mob);
    }

    @SubscribeEvent
    public void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob mob)) return;
        State state = levels.get(level);
        if (state == null) return;
        state.entities.remove(mob);
        UUID id = mob.getUUID();
        state.active.remove(id); state.gast.remove(id); state.mounds.remove(id); state.tendrils.remove(id);
        state.grantedAt.remove(id);
        state.wakeChecks.remove(id);
        TargetAcquisitionController.forget(id);
        com.arxyt.sporeperformance.runtime.GeneralPathBackoff.forget(id);
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        State state = levels.get(level);
        if (state == null) return;
        int rotation = PerformanceConfig.WORKING_ROTATION_TICKS.get();
        if (state.lastRefresh == Long.MIN_VALUE || level.getGameTime() - state.lastRefresh >= rotation) refresh(level, state);
    }

    public boolean mayWork(Mob mob, WorkKind kind) {
        if (!(mob.level() instanceof ServerLevel level)) return true;
        State state = stateFor(level, mob);
        int configuredLimit = limitFor(kind);
        if (configuredLimit == 0) return true;
        // Never grant every newly-loaded entity one unrestricted work window
        // before the first 200-tick rotation.  The first level END tick builds
        // the set; delaying one background cycle is safe and bounded.
        if (state.lastRefresh == Long.MIN_VALUE) return false;
        Set<UUID> selected = switch (kind) {
            case GASTGEBER -> state.gast;
            case MOUND -> state.mounds;
            case TENDRIL -> state.tendrils;
        };
        boolean allowed = selected.contains(mob.getUUID());
        if (!allowed) {
            PerformanceMetrics.increment("work." + kind.name().toLowerCase(java.util.Locale.ROOT) + ".suppressed");
            if (DebugTrace.enabled(DebugTrace.Category.BACKGROUND))
                DebugTrace.event(DebugTrace.Category.BACKGROUND, level, DebugTrace.trace(mob), mob,
                        "work_suppressed", "kind=" + kind + ",selected=" + selected.size());
        }
        return allowed;
    }

    public boolean isDormant(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        if (!RemoteIdleAiController.isManagedFamily(mob)) return false;
        int limit = PerformanceConfig.WORKING_FUNGAL_UNITS.get();
        if (limit == 0) return false;
        State state = stateFor(level, mob);
        UUID id = mob.getUUID();
        if (state.active.contains(id)) return false;

        long now = level.getGameTime();
        if (!needsImmediateWork(level, state, mob, now)) return true;
        if (claimCriticalWork(level, state, mob, now, limit)) return false;

        // The cap is deliberately strict.  Under an extreme simultaneous
        // combat burst, a newly-awakened distant unit waits for the next slot
        // instead of allowing the active set to grow without bound.
        PerformanceMetrics.increment("ai.population_critical_cap_rejected");
        return true;
    }

    private void refresh(ServerLevel level, State state) {
        long now = level.getGameTime();
        state.entities.removeIf(mob -> mob.isRemoved() || mob.level() != level);
        purgeStaleIds(state);
        List<Candidate> sorted = new ArrayList<>(state.entities.size());
        for (Mob mob : state.entities) {
            double nearest = nearestPlayerDistance(level, mob);
            sorted.add(new Candidate(mob, priority(state, mob, now, nearest), nearest));
        }
        int epoch = (int) (now / Math.max(1, PerformanceConfig.WORKING_ROTATION_TICKS.get()));
        sorted.sort(Comparator
                .comparingInt(Candidate::priority).reversed()
                .thenComparingDouble(Candidate::nearestPlayerDistance)
                .thenComparingInt(candidate -> Integer.rotateLeft(candidate.mob().getUUID().hashCode() ^ epoch, 7)));

        replace(state.active, select(sorted, PerformanceConfig.WORKING_FUNGAL_UNITS.get(), null, true), state, now);
        replace(state.gast, select(sorted, PerformanceConfig.WORKING_GASTGEBERS.get(), WorkKind.GASTGEBER, false), state, now);
        replace(state.mounds, select(sorted, PerformanceConfig.WORKING_MOUNDS.get(), WorkKind.MOUND, false), state, now);
        replace(state.tendrils, select(sorted, PerformanceConfig.WORKING_TENDRILS.get(), WorkKind.TENDRIL, false), state, now);
        state.lastRefresh = now;
        PerformanceMetrics.increment("work.refresh");
        if (DebugTrace.enabled(DebugTrace.Category.BACKGROUND))
            DebugTrace.state(DebugTrace.Category.BACKGROUND, level, 0L, null, "work_budget_refreshed",
                    "loaded=" + state.entities.size() + ",active=" + state.active.size() + ",gast=" + state.gast.size()
                            + ",mounds=" + state.mounds.size() + ",tendrils=" + state.tendrils.size());
    }

    private static int priority(State state, Mob mob, long now, double nearestPlayerDistance) {
        if (mob.getTarget() != null || mob.hurtTime > 0) return 4;
        if (nearestPlayerDistance <= 32.0D * 32.0D) return 3;
        Long granted = state.grantedAt.get(mob.getUUID());
        if (granted != null && now - granted < PerformanceConfig.WORKING_HYSTERESIS_TICKS.get()) return 2;
        return 1;
    }

    private static double nearestPlayerDistance(ServerLevel level, Mob mob) {
        double best = Double.MAX_VALUE;
        for (var player : level.players()) if (!player.isSpectator()) best = Math.min(best, player.distanceToSqr(mob));
        return best;
    }

    private static Set<UUID> select(List<Candidate> sorted, int limit, WorkKind kind, boolean managedOnly) {
        Set<UUID> result = new HashSet<>();
        if (limit == 0) {
            for (Candidate candidate : sorted) {
                Mob mob = candidate.mob();
                if ((!managedOnly || RemoteIdleAiController.isManagedFamily(mob))
                        && (kind == null || kindOf(mob) == kind)) result.add(mob.getUUID());
            }
            return result;
        }
        for (Candidate candidate : sorted) {
            Mob mob = candidate.mob();
            if (managedOnly && !RemoteIdleAiController.isManagedFamily(mob)) continue;
            if (kind != null && kindOf(mob) != kind) continue;
            result.add(mob.getUUID());
            if (result.size() >= limit) break;
        }
        return result;
    }

    private static void replace(Set<UUID> target, Set<UUID> selected, State state, long now) {
        for (UUID id : selected) if (!target.contains(id)) state.grantedAt.put(id, now);
        target.clear(); target.addAll(selected);
    }

    private static int limitFor(WorkKind kind) {
        return switch (kind) {
            case GASTGEBER -> PerformanceConfig.WORKING_GASTGEBERS.get();
            case MOUND -> PerformanceConfig.WORKING_MOUNDS.get();
            case TENDRIL -> PerformanceConfig.WORKING_TENDRILS.get();
        };
    }

    private State stateFor(ServerLevel level, Mob mob) {
        State state = levels.computeIfAbsent(level, ignored -> new State());
        state.entities.add(mob);
        return state;
    }

    private static boolean needsImmediateWork(ServerLevel level, State state, Mob mob, long now) {
        if (mob.getTarget() != null || mob.hurtTime > 0) return true;
        UUID id = mob.getUUID();
        WakeCheck wake = state.wakeChecks.get(id);
        if (wake == null || now >= wake.nextCheckTick) {
            boolean nearby = !com.arxyt.sporeperformance.ai.FungalAiRuntime.query(level, mob,
                    mob.getBoundingBox().inflate(32.0D), Player.class).isEmpty();
            wake = new WakeCheck(nearby, now + 5L);
            state.wakeChecks.put(id, wake);
        }
        return wake.nearby;
    }

    private static boolean claimCriticalWork(ServerLevel level, State state, Mob mob, long now, int limit) {
        UUID id = mob.getUUID();
        if (state.active.contains(id)) return true;
        if (state.active.size() < limit) {
            state.active.add(id);
            state.grantedAt.put(id, now);
            PerformanceMetrics.increment("ai.population_critical_claimed");
            return true;
        }

        UUID replacement = null;
        for (UUID candidateId : state.active) {
            Mob candidate = find(state, candidateId);
            if (candidate == null || candidate.isRemoved() || !needsImmediateWork(level, state, candidate, now)) {
                replacement = candidateId;
                break;
            }
        }
        if (replacement == null) return false;
        state.active.remove(replacement);
        state.grantedAt.remove(replacement);
        state.active.add(id);
        state.grantedAt.put(id, now);
        PerformanceMetrics.increment("ai.population_critical_preempted");
        return true;
    }

    private static Mob find(State state, UUID id) {
        for (Mob mob : state.entities) if (mob.getUUID().equals(id)) return mob;
        return null;
    }

    private static void purgeStaleIds(State state) {
        Set<UUID> existing = new HashSet<>();
        for (Mob mob : state.entities) existing.add(mob.getUUID());
        state.active.retainAll(existing);
        state.gast.retainAll(existing);
        state.mounds.retainAll(existing);
        state.tendrils.retainAll(existing);
        state.grantedAt.keySet().retainAll(existing);
        state.wakeChecks.keySet().retainAll(existing);
    }

    public List<String> statusLines() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<ServerLevel, State> entry : levels.entrySet()) {
            State s = entry.getValue();
            lines.add("Work " + entry.getKey().dimension().location() + ": loaded=" + s.entities.size()
                    + ", active=" + s.active.size() + ", GastGeber=" + s.gast.size()
                    + ", Mound=" + s.mounds.size() + ", Tendril=" + s.tendrils.size());
        }
        return lines;
    }

    public void clear() { levels.clear(); }
    private static boolean isSpore(Mob mob) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return key != null && "spore".equals(key.getNamespace());
    }
    private static WorkKind kindOf(Mob mob) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (key == null || !"spore".equals(key.getNamespace())) return null;
        return switch (key.getPath()) {
            case "gastgaber" -> WorkKind.GASTGEBER;
            case "mound" -> WorkKind.MOUND;
            case "tendril" -> WorkKind.TENDRIL;
            default -> null;
        };
    }
    private static final class State {
        private final Set<Mob> entities = new LinkedHashSet<>();
        private final Set<UUID> active = new HashSet<>(), gast = new HashSet<>(), mounds = new HashSet<>(), tendrils = new HashSet<>();
        private final Map<UUID, Long> grantedAt = new HashMap<>();
        private final Map<UUID, WakeCheck> wakeChecks = new HashMap<>();
        private long lastRefresh = Long.MIN_VALUE;
    }
    private record Candidate(Mob mob, int priority, double nearestPlayerDistance) {}
    private static final class WakeCheck {
        private final boolean nearby;
        private final long nextCheckTick;
        private WakeCheck(boolean nearby, long nextCheckTick) {
            this.nearby = nearby;
            this.nextCheckTick = nextCheckTick;
        }
    }
    private FungalWorkBudget() {}
}
