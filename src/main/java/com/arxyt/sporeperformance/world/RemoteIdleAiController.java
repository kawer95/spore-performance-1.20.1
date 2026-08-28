package com.arxyt.sporeperformance.world;

import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.Harbinger.Spore.Sentities.BaseEntities.Hyper;
import com.Harbinger.Spore.Sentities.BaseEntities.Organoid;
import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.Map;
import java.util.WeakHashMap;

/** Decides once per entity tick whether only goal selectors may be skipped for a distant idle infected. */
@Mod.EventBusSubscriber(modid = SporePerformance.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RemoteIdleAiController {
    private static final Map<Mob, State> STATES = new WeakHashMap<>();
    private static final ClassValue<Boolean> SUPPORTED_FAMILY = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            if (Hyper.class.isAssignableFrom(type)) return true;
            String name = type.getName();
            return name.startsWith("com.Harbinger.Spore.Sentities.BasicInfected.")
                    || name.startsWith("com.Harbinger.Spore.Sentities.EvolvedInfected.");
        }
    };
    private static volatile ConfigSnapshot config = new ConfigSnapshot(false, 96, 10);

    @SubscribeEvent
    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == PerformanceConfig.COMMON_SPEC) refreshFromConfig();
    }

    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == PerformanceConfig.COMMON_SPEC) refreshFromConfig();
    }

    /** Re-reads the snapshot once Forge has bound the common config. */
    public static void refreshFromConfig() {
        try {
            config = new ConfigSnapshot(PerformanceConfig.AGGRESSIVE_REMOTE_IDLE_AI.get(),
                    PerformanceConfig.AGGRESSIVE_REMOTE_AI_DISTANCE.get(), PerformanceConfig.AGGRESSIVE_REMOTE_AI_INTERVAL.get());
        } catch (IllegalStateException notBoundYet) {
            // Config Loading can be delivered before ConfigValue#get is usable on
            // a client/dev launch. The previous immutable snapshot is safe.
        }
    }

    public static boolean skipSelectors(Mob mob) {
        if (FungalWorkBudget.INSTANCE.isDormant(mob)) {
            int interval = 20;
            boolean skip = Math.floorMod(mob.tickCount + mob.getUUID().hashCode(), interval) != 0;
            if (skip) PerformanceMetrics.increment("ai.population_dormant_selector_skipped");
            return skip;
        }
        if (PerformanceConfig.REFACTOR_AI_ENABLED.get()) return false;
        ConfigSnapshot current = config;
        if (!current.enabled
                || mob.level().isClientSide || !isSupportedFamily(mob) || mob instanceof Calamity || mob instanceof Organoid) return false;
        long now = mob.level().getGameTime();
        synchronized (STATES) {
            State state = STATES.computeIfAbsent(mob, ignored -> new State(mob.getHealth(), now));
            if (state.evaluatedTick == now) return state.skip;
            state.evaluatedTick = now;
            if (mob.getHealth() < state.health) state.lastDamageTick = now;
            state.health = mob.getHealth();
            boolean nearPlayer = mob.level().getNearestPlayer(mob, current.distance) != null;
            state.skip = !nearPlayer && mob.getTarget() == null && !mob.isPassenger()
                    && now - state.lastDamageTick >= 100L
                    && mob.tickCount % current.interval != 0;
            if (state.skip) PerformanceMetrics.increment("ai.remote_selector_skipped");
            return state.skip;
        }
    }

    public static void clear() { synchronized (STATES) { STATES.clear(); } }

    /**
     * Returns true only for a work-token-suspended infected.  The caller is
     * Mob.serverAiStep, after entity physics but before vanilla sensing,
     * goals, navigation and movement controls.  Bosses and organoids stay
     * out of this path by design; their own work budgets remain independent.
     */
    public static boolean suspendServerAi(Mob mob) {
        if (mob.level().isClientSide || !isManagedFamily(mob) || mob.isPassenger()) return false;
        if (!FungalWorkBudget.INSTANCE.isDormant(mob)) return false;
        PerformanceMetrics.increment("ai.population_dormant_server_ai_suspended");
        return true;
    }

    /** Restrict throttling to the promised Basic/Evolved/Hyper families, never experiments or utilities. */
    public static boolean isManagedFamily(Mob mob) {
        return SUPPORTED_FAMILY.get(mob.getClass());
    }

    private static boolean isSupportedFamily(Mob mob) { return isManagedFamily(mob); }

    private static final class State {
        private float health;
        private long lastDamageTick;
        private long evaluatedTick = Long.MIN_VALUE;
        private boolean skip;
        private State(float health, long now) { this.health = health; this.lastDamageTick = now; }
    }
    private record ConfigSnapshot(boolean enabled, int distance, int interval) {}
    private RemoteIdleAiController() {}
}
