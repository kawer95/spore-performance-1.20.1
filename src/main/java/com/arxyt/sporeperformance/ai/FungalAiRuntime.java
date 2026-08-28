package com.arxyt.sporeperformance.ai;

import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import com.arxyt.sporeperformance.diagnostics.DebugTrace;
import com.arxyt.sporeperformance.diagnostics.CalamityTrace;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Forge lifecycle owner and per-dimension service registry for the AI refactor. */
public final class FungalAiRuntime {
    public static final FungalAiRuntime INSTANCE = new FungalAiRuntime();
    private final Map<ServerLevel, LevelRuntime> levels = new IdentityHashMap<>();

    public LevelRuntime get(ServerLevel level) {
        return levels.computeIfAbsent(level, LevelRuntime::new);
    }

    public LevelRuntime existing(ServerLevel level) { return levels.get(level); }

    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof LivingEntity living) {
            get(level).index.add(living);
            if (DebugTrace.enabled(DebugTrace.Category.LIFECYCLE) && isSpore(living))
                DebugTrace.event(DebugTrace.Category.LIFECYCLE, level, DebugTrace.trace(living), living, "entity_join", "indexed=true");
        }
    }

    @SubscribeEvent
    public void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof LivingEntity living) {
            LevelRuntime runtime = levels.get(level);
            if (runtime != null) {
                runtime.index.remove(living);
                if (living instanceof Calamity calamity) runtime.calamities.remove(calamity.getUUID());
            }
            if (DebugTrace.enabled(DebugTrace.Category.LIFECYCLE) && isSpore(living))
                DebugTrace.event(DebugTrace.Category.LIFECYCLE, level, DebugTrace.trace(living), living, "entity_leave", "removed_from_index=true");
        }
    }

    /**
     * Chunk unload does not necessarily emit EntityLeaveLevelEvent.  Without this hook every
     * passive mob encountered during a session remained in the shared perception index.
     */
    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        LevelRuntime runtime = levels.get(level);
        if (runtime == null) return;
        int removed = runtime.index.removeChunk(event.getChunk().getPos().toLong());
        runtime.calamities.removeChunk(event.getChunk().getPos().toLong());
        if (removed > 0 && DebugTrace.enabled(DebugTrace.Category.LIFECYCLE)) {
            DebugTrace.state(DebugTrace.Category.LIFECYCLE, level, 0L, null, "chunk_unload_index_cleanup",
                    "chunk=" + event.getChunk().getPos() + ",removed=" + removed + ",remaining=" + runtime.index.size());
        }
    }

    @SubscribeEvent
    public void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity living = event.getEntity();
        if (!(living.level() instanceof ServerLevel level)) return;
        LevelRuntime runtime = get(level);
        runtime.index.update(living);
        if (living instanceof Calamity calamity) {
            if (living.isAlive()) runtime.calamities.tick(calamity);
            else runtime.calamities.remove(calamity.getUUID());
            CalamityTrace.INSTANCE.recordRuntimeTick(calamity, level, runtime);
        }
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        LevelRuntime runtime = levels.get(level);
        if (runtime != null) runtime.tick(level.getGameTime());
    }

    @SubscribeEvent
    public void onBlockChanged(BlockEvent event) {
        LevelAccessor accessor = event.getLevel();
        if (!(accessor instanceof ServerLevel level)) return;
        LevelRuntime runtime = levels.get(level);
        if (runtime != null) runtime.paths.markTerrainChanged(event.getPos());
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            CalamityTrace.INSTANCE.clearLevel(level);
            remove(level);
            // ExpAirPathNavigation uses a one-entry region cache rather than a
            // level map.  Explicitly drop it when a dimension goes away so a
            // stopped/reloaded world is never retained by the static cache.
            AirSweepContext.clear();
        }
    }

    public void clear() {
        new ArrayList<>(levels.values()).forEach(LevelRuntime::close);
        levels.clear();
        AirSweepContext.clear();
    }

    public List<String> statusLines() {
        List<String> lines = new ArrayList<>();
        lines.add("AI refactor: enabled=" + PerformanceConfig.REFACTOR_AI_ENABLED.get()
                + ", perception=" + PerformanceConfig.REFACTOR_SHARED_PERCEPTION.get()
                + ", threats=" + PerformanceConfig.REFACTOR_EVENT_THREATS.get()
                + ", groups=" + PerformanceConfig.REFACTOR_GROUP_COORDINATION.get()
                + ", navigation=" + PerformanceConfig.REFACTOR_NAVIGATION_ENABLED.get()
                + ", calamityNavigation=" + PerformanceConfig.REFACTOR_CALAMITY_NAVIGATION_ENABLED.get());
        levels.forEach((level, runtime) -> lines.add(level.dimension().location() + ": livingIndexed=" + runtime.index.size()
                + ", sections=" + runtime.index.sectionCount() + ", frames=" + runtime.perception.frameCount()
                + ", pathQueue=" + runtime.paths.queued() + ", pathInFlight=" + runtime.paths.inFlight()
                + ", corridors=" + runtime.paths.corridorCount() + ", nativePaths=" + runtime.paths.nativeCacheCount()
                + ", " + runtime.calamities.status()));
        return lines;
    }

    public static boolean isSpore(Entity entity) {
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key != null && "spore".equals(key.getNamespace());
    }

    public static <T extends LivingEntity> List<T> query(ServerLevel level, Entity source, AABB bounds, Class<T> type) {
        LevelRuntime runtime = INSTANCE.get(level);
        List<T> result = runtime.perception.candidates(level.getGameTime(), source, bounds, type);
        if (DebugTrace.enabled(DebugTrace.Category.PERCEPTION)) {
            DebugTrace.event(DebugTrace.Category.PERCEPTION, level, DebugTrace.trace(source), source, "shared_query",
                    "requestedType=" + type.getName() + ",candidates=" + result.size() + ",bounds=" + bounds.getSize());
        }
        return result;
    }

    private void remove(ServerLevel level) {
        LevelRuntime runtime = levels.remove(level);
        if (runtime != null) {
            if (DebugTrace.enabled(DebugTrace.Category.LIFECYCLE))
                DebugTrace.state(DebugTrace.Category.LIFECYCLE, level, 0L, null, "level_unload",
                        "livingIndexed=" + runtime.index.size() + ",pathQueue=" + runtime.paths.queued());
            runtime.close();
        }
    }

    public static final class LevelRuntime implements AutoCloseable {
        public final FungalEntityIndex index = new FungalEntityIndex();
        public final SharedPerceptionService perception = new SharedPerceptionService(index);
        public final FungalGroupCoordinator groups = new FungalGroupCoordinator(perception);
        public final FungalPathService paths;
        public final CalamityNavigationRuntime calamities;
        private long lastTick = Long.MIN_VALUE;

        private LevelRuntime(ServerLevel level) {
            paths = new FungalPathService(level);
            calamities = new CalamityNavigationRuntime(level);
        }
        private void tick(long tick) {
            if (lastTick == tick) return;
            lastTick = tick;
            perception.beginTick(tick);
            if (PerformanceConfig.REFACTOR_AI_ENABLED.get() && PerformanceConfig.REFACTOR_NAVIGATION_ENABLED.get()) {
                try { paths.tick(); }
                catch (RuntimeException exception) {
                    DebugTrace.fault(paths.level(), 0L, null, "path_service_tick", exception);
                    throw exception;
                }
            }
            PerformanceMetrics.increment("ai_refactor.tick_pipeline.level_ticks");
        }
        @Override public void close() { calamities.clear(); paths.close(); perception.clear(); index.clear(); }
    }

    private FungalAiRuntime() {}
}
