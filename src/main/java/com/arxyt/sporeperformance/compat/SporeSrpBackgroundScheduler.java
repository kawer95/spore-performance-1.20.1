package com.arxyt.sporeperformance.compat;

import com.maha_fish.sporesrp.util.CasingBuilder;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared, loaded-chunk-only cursor service for sporesrp surface searches and Hivemind casings. */
public final class SporeSrpBackgroundScheduler {
    public static final SporeSrpBackgroundScheduler INSTANCE = new SporeSrpBackgroundScheduler();
    private static final long RESULT_TTL = 1_200L;
    private final ArrayDeque<Job> jobs = new ArrayDeque<>();
    private final Map<JobKey, Job> active = new HashMap<>();
    private final Map<JobKey, Job> deferred = new LinkedHashMap<>();
    private final Map<JobKey, SurfaceResult> surfaceResults = new HashMap<>();

    public synchronized BlockPos findSurface(ServerLevel level, BlockPos center, int radius, SurfaceKind kind) {
        if (!PerformanceConfig.AGGRESSIVE_SPORESRP_SURFACE_SEARCH.get()) {
            return findSurfaceSynchronously(level, center, radius);
        }
        JobKey key = JobKey.surface(level.dimension(), center, radius, kind);
        SurfaceResult result = surfaceResults.get(key);
        if (result != null && result.position != null) return result.position;
        // An empty loaded-area search is deliberately retried on the next native skill
        // opportunity: a formerly unavailable chunk may have become relevant meanwhile.
        if (result != null) surfaceResults.remove(key);
        if (!active.containsKey(key) && !deferred.containsKey(key)) enqueue(new SurfaceJob(key));
        return null;
    }

    public synchronized void buildCasing(Level level, BlockPos center, int radius, int thickness) {
        if (!PerformanceConfig.AGGRESSIVE_SPORESRP_CASING_SCHEDULER.get() || !(level instanceof ServerLevel server)) {
            CasingBuilder.buildCasing(level, center, radius, thickness);
            return;
        }
        enqueue(new CasingJob(JobKey.casing(server.dimension(), center, radius, thickness)));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        synchronized (this) {
            if (!PerformanceConfig.AGGRESSIVE_SPORESRP_SURFACE_SEARCH.get() && !PerformanceConfig.AGGRESSIVE_SPORESRP_CASING_SCHEDULER.get()) {
                clear();
                return;
            }
            if (!PerformanceConfig.AGGRESSIVE_SPORESRP_SURFACE_SEARCH.get()) clearKind(SurfaceJob.class);
            if (!PerformanceConfig.AGGRESSIVE_SPORESRP_CASING_SCHEDULER.get()) clearKind(CasingJob.class);
            SporeSrpBlockBudget.beginTick(event.getServer().getTickCount());
            promoteDeferred();
            drain(event.getServer());
            long now = event.getServer().getTickCount();
            surfaceResults.entrySet().removeIf(entry -> entry.getValue().completedAt + RESULT_TTL < now);
        }
    }

    private void enqueue(Job job) {
        if (active.containsKey(job.key) || deferred.containsKey(job.key)) return;
        if (jobs.size() >= PerformanceConfig.AGGRESSIVE_SPORESRP_BACKGROUND_MAX_JOBS.get()) {
            deferred.put(job.key, job);
            PerformanceMetrics.increment("sporesrp.background_queue_full");
            return;
        }
        jobs.addLast(job);
        active.put(job.key, job);
        PerformanceMetrics.increment("sporesrp.background_queued");
    }

    private void drain(MinecraftServer server) {
        int turns = jobs.size();
        while (turns-- > 0 && !jobs.isEmpty()) {
            Job job = jobs.removeFirst();
            ServerLevel level = server.getLevel(job.key.dimension);
            if (level == null) {
                finish(job, null, server.getTickCount());
                continue;
            }
            int permitted = SporeSrpBlockBudget.reserve(job.perTaskBudget);
            if (permitted == 0) {
                jobs.addFirst(job);
                break;
            }
            job.run(level, permitted);
            if (job.complete) finish(job, job instanceof SurfaceJob surface ? surface.result : null, server.getTickCount());
            else jobs.addLast(job);
        }
    }

    private void finish(Job job, BlockPos result, long completedAt) {
        active.remove(job.key);
        if (job instanceof SurfaceJob) surfaceResults.put(job.key, new SurfaceResult(result, completedAt));
    }

    public synchronized void clear() {
        jobs.clear();
        active.clear();
        deferred.clear();
        surfaceResults.clear();
    }

    /** A live config disable cancels only the now-disabled kind; native work resumes on its next cycle. */
    private void clearKind(Class<? extends Job> type) {
        jobs.removeIf(type::isInstance);
        active.entrySet().removeIf(entry -> type.isInstance(entry.getValue()));
        deferred.entrySet().removeIf(entry -> type.isInstance(entry.getValue()));
        if (type == SurfaceJob.class) surfaceResults.clear();
    }

    /** Requests that arrived at capacity are retained and admitted in insertion order. */
    private void promoteDeferred() {
        int capacity = PerformanceConfig.AGGRESSIVE_SPORESRP_BACKGROUND_MAX_JOBS.get();
        var iterator = deferred.entrySet().iterator();
        while (jobs.size() < capacity && iterator.hasNext()) {
            Job job = iterator.next().getValue();
            jobs.addLast(job);
            active.put(job.key, job);
            iterator.remove();
        }
    }

    /** Exact fallback used while the independent surface scheduler is off. */
    public static BlockPos findSurfaceSynchronously(ServerLevel level, BlockPos center, int maxRadius) {
        for (int radius = 1; radius <= maxRadius; ++radius) {
            for (int dx = -radius; dx <= radius; ++dx) {
                for (int dy = -radius; dy <= radius; ++dy) {
                    for (int dz = -radius; dz <= radius; ++dz) {
                        if (Math.abs(dx) == radius || Math.abs(dy) == radius || Math.abs(dz) == radius) {
                            BlockPos pos = center.offset(dx, dy, dz);
                            if (isValidSurface(level, pos)) return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean isValidSurface(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && !state.canBeReplaced()) return false;
        BlockState above = level.getBlockState(pos.above());
        return (above.isAir() || above.canBeReplaced()) && level.getBlockState(pos.below()).isSolid();
    }

    private static boolean isValidLoadedSurface(ServerLevel level, BlockPos pos) {
        // The shell can straddle a chunk edge. Verify every queried coordinate before
        // getBlockState so aggressive mode never asks ServerLevel to obtain a new chunk.
        return level.hasChunkAt(pos) && level.hasChunkAt(pos.above()) && level.hasChunkAt(pos.below())
                && isValidSurface(level, pos);
    }

    public enum SurfaceKind { PROTO, FULL_HIVEMIND }

    private abstract static class Job {
        protected final JobKey key;
        protected final int perTaskBudget;
        protected boolean complete;
        private Job(JobKey key) {
            this.key = key;
            // Per-task work is intentionally snapshotted. A config reload affects only
            // newly queued work, while the global shared admission budget remains live.
            this.perTaskBudget = PerformanceConfig.AGGRESSIVE_SPORESRP_BACKGROUND_PER_TASK.get();
        }
        abstract void run(ServerLevel level, int budget);
    }

    private static final class SurfaceJob extends Job {
        private int radius = 1;
        private int dx = -1;
        private int dy = -1;
        private int dz = -1;
        private BlockPos result;
        private SurfaceJob(JobKey key) { super(key); }

        @Override void run(ServerLevel level, int budget) {
            int used = 0;
            while (used < budget && !complete) {
                if (radius > key.radius) { complete = true; break; }
                // Count every cursor position, rather than only shell positions.  Otherwise
                // the skipped cube interior would make a large radius search effectively
                // unbounded on one tick despite the nominal work budget.
                ++used;
                if (Math.abs(dx) == radius || Math.abs(dy) == radius || Math.abs(dz) == radius) {
                    BlockPos pos = key.center.offset(dx, dy, dz);
                    if (isValidLoadedSurface(level, pos)) {
                        result = pos;
                        complete = true;
                        break;
                    }
                }
                advance();
            }
        }

        private void advance() {
            if (++dz <= radius) return;
            dz = -radius;
            if (++dy <= radius) return;
            dy = -radius;
            if (++dx <= radius) return;
            ++radius;
            dx = -radius;
            dy = -radius;
            dz = -radius;
        }
    }

    private static final class CasingJob extends Job {
        private static List<BlockState> pool;
        private final int side;
        private final int total;
        private final RandomSource random = RandomSource.create();
        private int cursor;
        private CasingJob(JobKey key) {
            super(key);
            side = key.radius * 2 + 1;
            total = side * side * side;
        }

        @Override void run(ServerLevel level, int budget) {
            int used = 0;
            while (used++ < budget && cursor < total) {
                int index = cursor++;
                int x = index % side - key.radius;
                int yz = index / side;
                int z = yz % side - key.radius;
                int y = yz / side - key.radius;
                double distance = Mth.sqrt((float) (x * x + y * y + z * z));
                if (distance <= key.radius - key.thickness / 2.0D || distance >= key.radius + key.thickness / 2.0D) continue;
                BlockPos target = key.center.offset(x, y, z);
                if (!level.hasChunkAt(target)) continue;
                BlockState current = level.getBlockState(target);
                if (random.nextFloat() >= 0.1F || current.isSolidRender(level, target) || current.is(Blocks.WATER)) continue;
                List<BlockState> states = blockPool();
                level.setBlock(target, states.get(random.nextInt(states.size())), 3);
            }
            if (cursor >= total) complete = true;
        }

        private static List<BlockState> blockPool() {
            if (pool != null) return pool;
            List<BlockState> result = new ArrayList<>();
            for (String id : List.of("spore:biomass_block", "spore:rooted_biomass", "spore:calcified_biomass_block", "spore:sicken_biomass_block", "spore:gastric_biomass_block")) {
                Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(id));
                if (block == null || block == Blocks.AIR) continue;
                BlockState state = block.defaultBlockState();
                result.add(state); result.add(state); result.add(state);
            }
            // Keep sporesrp's fallback weighting: three stone entries are always present,
            // rather than replacing every missing optional block with three stone entries.
            result.add(Blocks.STONE.defaultBlockState());
            result.add(Blocks.STONE.defaultBlockState());
            result.add(Blocks.STONE.defaultBlockState());
            pool = result;
            return pool;
        }
    }

    private record JobKey(ResourceKey<Level> dimension, BlockPos center, int radius, int thickness, SurfaceKind kind) {
        private static JobKey surface(ResourceKey<Level> dimension, BlockPos center, int radius, SurfaceKind kind) {
            return new JobKey(dimension, center.immutable(), radius, 0, kind);
        }
        private static JobKey casing(ResourceKey<Level> dimension, BlockPos center, int radius, int thickness) {
            return new JobKey(dimension, center.immutable(), radius, thickness, null);
        }
    }
    private record SurfaceResult(BlockPos position, long completedAt) {}
    private SporeSrpBackgroundScheduler() {}
}
