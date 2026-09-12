package com.arxyt.sporeperformance.scheduler;

import com.Harbinger.Spore.Sentities.Calamities.Howitzer;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Incremental, loaded-chunk-only replacement for Howitzer's 65x9x65 ore scan.
 *
 * <p>The original scan visits roughly 38k blocks on one entity Tick.  Jobs retain only UUIDs,
 * primitive cursor state and an immutable scan box; entities and chunks are resolved for each
 * bounded slice so unloading a level cannot be prevented by this scheduler.</p>
 */
public final class HowitzerOreSearchScheduler {
    public static final HowitzerOreSearchScheduler INSTANCE = new HowitzerOreSearchScheduler();
    private static final TagKey<Block> ORES = BlockTags.create(new ResourceLocation("forge", "ores"));
    private static final int TIME_CHECK_STRIDE = 16;
    private final Map<ServerLevel, LevelJobs> levels = new IdentityHashMap<>();

    public synchronized boolean queue(Howitzer howitzer) {
        if (!PerformanceConfig.REFACTOR_HOWITZER_INCREMENTAL_ORE_SCAN.get()
                || !(howitzer.level() instanceof ServerLevel level)) return false;
        LevelJobs state = levels.computeIfAbsent(level, ignored -> new LevelJobs());
        UUID id = howitzer.getUUID();
        if (!state.queued.add(id)) return true;
        AABB box = howitzer.getBoundingBox().inflate(32.0D, 4.0D, 32.0D);
        state.jobs.addLast(new Job(id, new HowitzerOreScanCursor(
                (int) Math.floor(box.minX), (int) Math.floor(box.minY), (int) Math.floor(box.minZ),
                (int) Math.floor(box.maxX), (int) Math.floor(box.maxY), (int) Math.floor(box.maxZ))));
        PerformanceMetrics.increment("ai_refactor.howitzer.ore_scan_queued");
        return true;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !PerformanceConfig.REFACTOR_HOWITZER_INCREMENTAL_ORE_SCAN.get()) return;
        long started = System.nanoTime();
        long deadline = started + PerformanceConfig.REFACTOR_HOWITZER_ORE_TIME_BUDGET_MICROS.get() * 1_000L;
        int remaining = PerformanceConfig.REFACTOR_HOWITZER_ORE_POSITIONS_PER_TICK.get();
        synchronized (this) {
            for (ServerLevel level : event.getServer().getAllLevels()) {
                if (remaining <= 0 || reached(deadline)) break;
                LevelJobs state = levels.get(level);
                if (state == null || state.jobs.isEmpty()) continue;
                int turns = state.jobs.size();
                while (remaining > 0 && turns-- > 0 && !state.jobs.isEmpty() && !reached(deadline)) {
                    Job job = state.jobs.removeFirst();
                    int used = job.run(level, Math.min(remaining,
                            PerformanceConfig.REFACTOR_HOWITZER_ORE_POSITIONS_PER_TASK_TICK.get()), deadline);
                    remaining -= Math.max(1, used);
                    if (job.complete) state.queued.remove(job.entityId);
                    else state.jobs.addLast(job);
                }
            }
        }
        PerformanceMetrics.add("ai_refactor.howitzer.ore_scan_scheduler_nanos", System.nanoTime() - started);
        if (reached(deadline)) PerformanceMetrics.increment("ai_refactor.howitzer.ore_scan_budget_hit");
    }

    @SubscribeEvent
    public synchronized void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) levels.remove(level);
    }

    public synchronized void clear() {
        levels.clear();
    }

    private static boolean reached(long deadline) {
        return System.nanoTime() - deadline >= 0L;
    }

    private static final class LevelJobs {
        private final ArrayDeque<Job> jobs = new ArrayDeque<>();
        private final Set<UUID> queued = new HashSet<>();
    }

    private static final class Job {
        private final UUID entityId;
        private final HowitzerOreScanCursor cursor;
        private final int[] xyz = new int[3];
        private boolean complete;

        private Job(UUID entityId, HowitzerOreScanCursor cursor) {
            this.entityId = entityId;
            this.cursor = cursor;
        }

        private int run(ServerLevel level, int budget, long deadline) {
            Entity resolved = level.getEntity(entityId);
            if (!(resolved instanceof Howitzer howitzer) || !howitzer.isAlive()) {
                complete = true;
                return 0;
            }
            int used = 0;
            int cachedChunkX = Integer.MIN_VALUE;
            int cachedChunkZ = Integer.MIN_VALUE;
            LevelChunk cachedChunk = null;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            while (used < budget && cursor.hasNext()) {
                if ((used & (TIME_CHECK_STRIDE - 1)) == 0 && reached(deadline)) break;
                cursor.next(xyz);
                used++;
                pos.set(xyz[0], xyz[1], xyz[2]);
                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;
                if (chunkX != cachedChunkX || chunkZ != cachedChunkZ) {
                    cachedChunkX = chunkX;
                    cachedChunkZ = chunkZ;
                    cachedChunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                }
                if (cachedChunk == null) continue;
                BlockState block = cachedChunk.getBlockState(pos);
                if (!block.is(ORES)) continue;
                PerformanceMetrics.increment("ai_refactor.howitzer.ore_candidates");
                if (howitzer.hasLineOfSightBlocks(pos) && howitzer.getRandom().nextFloat() < 0.5F) {
                    howitzer.setTargetPos(pos.immutable());
                    complete = true;
                    PerformanceMetrics.increment("ai_refactor.howitzer.ore_scan_found");
                    break;
                }
            }
            PerformanceMetrics.add("ai_refactor.howitzer.ore_scan_positions", used);
            if (!cursor.hasNext()) complete = true;
            return used;
        }
    }

    private HowitzerOreSearchScheduler() {}
}
