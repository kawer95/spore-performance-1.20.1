package com.arxyt.sporeperformance.scheduler;

import com.Harbinger.Spore.Core.SConfig;
import com.Harbinger.Spore.Core.Sblocks;
import com.Harbinger.Spore.Core.Sentities;
import com.Harbinger.Spore.SBlockEntities.LivingStructureBlocks;
import com.Harbinger.Spore.Sentities.Organoids.Mound;
import com.Harbinger.Spore.Sentities.Utility.InfectionTendril;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.compat.MoundStructureBridge;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import com.arxyt.sporeperformance.diagnostics.DebugTrace;
import com.arxyt.sporeperformance.world.FungalWorkBudget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.Harbinger.Spore.SBlockEntities.ContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Converts Mound's unbounded scans into bounded work only while the corresponding independent option is enabled.
 * A queue request is retained when full instead of falling back to the original synchronous scan.
 */
public final class FungalWorkScheduler {
    public static final FungalWorkScheduler INSTANCE = new FungalWorkScheduler();
    private static final int RETRY_TICKS = 100;
    private static final int TIME_CHECK_STRIDE = 64;
    private final ArrayDeque<WorkJob> tendrilJobs = new ArrayDeque<>();
    private final ArrayDeque<WorkJob> foliageJobs = new ArrayDeque<>();
    private final Set<JobKey> queued = new HashSet<>();
    private final Map<JobKey, DeferredRequest> deferred = new HashMap<>();

    public synchronized boolean queueTendril(Mound mound) {
        if (!tendrilSchedulingEnabled()) return false;
        JobKey key = new JobKey(mound.level().dimension(), mound.getUUID(), JobKind.TENDRIL);
        return queue(key, new TendrilJob(key, PerformanceConfig.AGGRESSIVE_TENDRIL_PER_TASK.get()), tendrilJobs, PerformanceConfig.AGGRESSIVE_TENDRIL_MAX_JOBS.get(), mound.level().getGameTime());
    }

    public synchronized boolean queueFoliage(Mound mound, double range, BlockPos origin) {
        if (!foliageSchedulingEnabled() || !SConfig.SERVER.mound_foliage.get()) return false;
        JobKey key = new JobKey(mound.level().dimension(), mound.getUUID(), JobKind.FOLIAGE);
        return queue(key, new FoliageJob(key, range, origin, PerformanceConfig.AGGRESSIVE_FOLIAGE_PER_TASK.get(),
                PerformanceConfig.AGGRESSIVE_FOLIAGE_FAST_CURSOR.get(), PerformanceConfig.AGGRESSIVE_FOLIAGE_DIRECT_CHUNK_READ.get()),
                foliageJobs, PerformanceConfig.AGGRESSIVE_FOLIAGE_MAX_JOBS.get(), mound.level().getGameTime());
    }

    private boolean queue(JobKey key, WorkJob job, ArrayDeque<WorkJob> jobs, int limit, long now) {
        if (queued.contains(key) || deferred.containsKey(key)) return true;
        if (jobs.size() >= limit) {
            deferred.put(key, new DeferredRequest(job, now + RETRY_TICKS));
            PerformanceMetrics.increment("fungal.deferred");
            return true;
        }
        jobs.addLast(job);
        queued.add(key);
        PerformanceMetrics.increment("fungal.queued");
        return true;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        boolean tendrilEnabled = tendrilSchedulingEnabled();
        boolean foliageEnabled = foliageSchedulingEnabled();
        if (!tendrilEnabled && !foliageEnabled) {
            clear();
            return;
        }
        if (!tendrilEnabled) clearKind(JobKind.TENDRIL);
        if (!foliageEnabled) clearKind(JobKind.FOLIAGE);
        for (ServerLevel level : event.getServer().getAllLevels()) drainDeferred(level);
        drain(event.getServer(), tendrilJobs, PerformanceConfig.AGGRESSIVE_TENDRIL_GLOBAL.get(), JobKind.TENDRIL);
        drain(event.getServer(), foliageJobs, PerformanceConfig.AGGRESSIVE_FOLIAGE_GLOBAL.get(), JobKind.FOLIAGE);
    }

    private static boolean refactorSchedulingEnabled() {
        return PerformanceConfig.REFACTOR_AI_ENABLED.get()
                && PerformanceConfig.REFACTOR_FOLIAGE_COMPILED_ACTION_PLANS.get();
    }

    private static boolean tendrilSchedulingEnabled() {
        return PerformanceConfig.AGGRESSIVE_MOUND_TENDRIL.get() || refactorSchedulingEnabled();
    }

    private static boolean foliageSchedulingEnabled() {
        return PerformanceConfig.AGGRESSIVE_FOLIAGE.get() || refactorSchedulingEnabled();
    }

    private synchronized void drainDeferred(ServerLevel level) {
        long now = level.getGameTime();
        var iterator = deferred.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            JobKey key = entry.getKey();
            DeferredRequest request = entry.getValue();
            if (!key.dimension.equals(level.dimension()) || request.retryAt > now) continue;
            ArrayDeque<WorkJob> destination = key.kind == JobKind.TENDRIL ? tendrilJobs : foliageJobs;
            int limit = key.kind == JobKind.TENDRIL ? PerformanceConfig.AGGRESSIVE_TENDRIL_MAX_JOBS.get() : PerformanceConfig.AGGRESSIVE_FOLIAGE_MAX_JOBS.get();
            if (destination.size() >= limit) {
                request.retryAt = now + RETRY_TICKS;
                continue;
            }
            destination.addLast(request.job);
            queued.add(key);
            iterator.remove();
        }
    }

    private synchronized void drain(net.minecraft.server.MinecraftServer server, ArrayDeque<WorkJob> jobs, int globalBudget, JobKind kind) {
        int remaining = globalBudget;
        int turns = jobs.size();
        boolean timed = PerformanceConfig.REFACTOR_AI_ENABLED.get()
                || (kind == JobKind.FOLIAGE
                ? PerformanceConfig.AGGRESSIVE_FOLIAGE_TIME_BUDGET.get()
                : PerformanceConfig.AGGRESSIVE_TENDRIL_TIME_BUDGET.get());
        int configuredMicros = kind == JobKind.FOLIAGE
                ? PerformanceConfig.AGGRESSIVE_FOLIAGE_TIME_BUDGET_MICROS.get()
                : PerformanceConfig.AGGRESSIVE_TENDRIL_TIME_BUDGET_MICROS.get();
        int refactorMicros = kind == JobKind.FOLIAGE
                ? PerformanceConfig.REFACTOR_FOLIAGE_TIME_BUDGET_MICROS.get()
                : PerformanceConfig.REFACTOR_TENDRIL_TIME_BUDGET_MICROS.get();
        int micros = Math.min(configuredMicros, refactorMicros);
        long deadline = timed ? System.nanoTime() + micros * 1_000L : Long.MAX_VALUE;
        while (remaining > 0 && turns-- > 0 && !jobs.isEmpty()) {
            if (deadlineReached(deadline)) break;
            WorkJob job = jobs.removeFirst();
            ServerLevel level = server.getLevel(job.key().dimension);
            int used = level == null ? 0 : job.run(level, Math.min(remaining, job.perTaskBudget()), deadline);
            remaining -= Math.max(1, used);
            if (job.complete()) {
                queued.remove(job.key());
                if (level != null && DebugTrace.enabled(DebugTrace.Category.BACKGROUND))
                    DebugTrace.event(DebugTrace.Category.BACKGROUND, level, 0L, level.getEntity(job.key().moundId),
                            "background_job_complete", "kind=" + kind + ",used=" + used + ",remainingQueue=" + jobs.size());
            } else {
                jobs.addLast(job);
                if (level != null && DebugTrace.enabled(DebugTrace.Category.BACKGROUND))
                    DebugTrace.event(DebugTrace.Category.BACKGROUND, level, 0L, level.getEntity(job.key().moundId),
                            "background_job_slice", "kind=" + kind + ",used=" + used + ",remainingBudget=" + remaining);
            }
        }
        if (timed && deadlineReached(deadline) && !jobs.isEmpty()) {
            PerformanceMetrics.increment("fungal." + kind.name().toLowerCase() + ".time_budget_hit");
            ServerLevel first = server.getAllLevels().iterator().hasNext() ? server.getAllLevels().iterator().next() : null;
            if (first != null && DebugTrace.enabled(DebugTrace.Category.BACKGROUND))
                DebugTrace.event(DebugTrace.Category.BACKGROUND, first, 0L, null,
                        "background_time_budget_hit", "kind=" + kind + ",queue=" + jobs.size());
        }
    }

    private static boolean deadlineReached(long deadline) {
        return deadline != Long.MAX_VALUE && System.nanoTime() - deadline >= 0L;
    }

    public synchronized void clear() {
        tendrilJobs.clear();
        foliageJobs.clear();
        queued.clear();
        deferred.clear();
    }

    private void clearKind(JobKind kind) {
        ArrayDeque<WorkJob> jobs = kind == JobKind.TENDRIL ? tendrilJobs : foliageJobs;
        jobs.forEach(job -> queued.remove(job.key()));
        jobs.clear();
        deferred.entrySet().removeIf(entry -> {
            if (entry.getKey().kind != kind) return false;
            queued.remove(entry.getKey());
            return true;
        });
    }

    private enum JobKind { TENDRIL, FOLIAGE }
    private record JobKey(ResourceKey<Level> dimension, UUID moundId, JobKind kind) {}
    private static final class DeferredRequest {
        private final WorkJob job;
        private long retryAt;
        private DeferredRequest(WorkJob job, long retryAt) { this.job = job; this.retryAt = retryAt; }
    }

    private interface WorkJob {
        JobKey key();
        int perTaskBudget();
        int run(ServerLevel level, int budget, long deadline);
        boolean complete();
    }

    private abstract static class MoundJob implements WorkJob {
        protected final JobKey key;
        private final int perTaskBudget;
        protected boolean complete;
        protected MoundJob(JobKey key, int perTaskBudget) { this.key = key; this.perTaskBudget = perTaskBudget; }
        @Override public JobKey key() { return key; }
        @Override public int perTaskBudget() { return perTaskBudget; }
        @Override public boolean complete() { return complete; }
        protected Mound mound(ServerLevel level) {
            Entity entity = level.getEntity(key.moundId);
            return entity instanceof Mound mound && mound.isAlive() ? mound : null;
        }
    }

    private static final class TendrilJob extends MoundJob {
        private int minX, minY, minZ, sizeX, sizeY, sizeZ, cursor;
        private boolean initialized;
        private TendrilJob(JobKey key, int perTaskBudget) { super(key, perTaskBudget); }

        @Override public int run(ServerLevel level, int budget, long deadline) {
            Mound mound = mound(level);
            if (mound == null) { complete = true; return 0; }
            if (!FungalWorkBudget.INSTANCE.mayWork(mound, FungalWorkBudget.WorkKind.MOUND)) return 0;
            if (!initialized) {
                AABB box = mound.getBoundingBox().inflate(SConfig.SERVER.mound_tendril_checker.get());
                minX = (int) Math.floor(box.minX); minY = (int) Math.floor(box.minY); minZ = (int) Math.floor(box.minZ);
                sizeX = (int) Math.floor(box.maxX) - minX + 1;
                sizeY = (int) Math.floor(box.maxY) - minY + 1;
                sizeZ = (int) Math.floor(box.maxZ) - minZ + 1;
                initialized = true;
            }
            int total = sizeX * sizeY * sizeZ;
            int used = 0;
            while (used < budget && cursor < total) {
                if ((used & (TIME_CHECK_STRIDE - 1)) == 0 && deadlineReached(deadline)) break;
                BlockPos pos = position(cursor++);
                used++;
                if (!level.hasChunkAt(pos)) continue;
                BlockState state = level.getBlockState(pos);
                if (!isFeedTarget(level, pos, state)) continue;
                InfectionTendril tendril = new InfectionTendril(Sentities.TENDRIL.get(), level);
                tendril.setAgeM(mound.getMaxAge() - 1);
                tendril.setSearchArea(pos);
                tendril.setPos(mound.getX(), mound.getY() + 0.5D, mound.getZ());
                level.addFreshEntity(tendril);
                complete = true;
                PerformanceMetrics.increment("fungal.tendril_found");
                break;
            }
            if (cursor >= total) complete = true;
            return used;
        }

        private BlockPos position(int index) {
            int x = index % sizeX;
            int yz = index / sizeX;
            int z = yz % sizeZ;
            int y = yz / sizeZ;
            return new BlockPos(minX + x, minY + y, minZ + z);
        }

        private boolean isFeedTarget(ServerLevel level, BlockPos pos, BlockState state) {
            BlockEntity entity = level.getBlockEntity(pos);
            if (entity instanceof LivingStructureBlocks) return true;
            if (SConfig.SERVER.tendril_chest.get() && entity instanceof Container container && !(container instanceof ContainerBlockEntity)
                    && container.hasAnyMatching(ItemStack::isEdible)) return true;
            return SConfig.SERVER.tendril_corpse.get() && state.is(Sblocks.REMAINS.get())
                    || SConfig.SERVER.tendril_spawner.get() && state.is(Blocks.SPAWNER);
        }
    }

    private static final class FoliageJob extends MoundJob {
        private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> STRUCTURE_TAG = BlockTags.create(new ResourceLocation("spore:block_st"));
        private final BlockPos origin;
        private final int radius;
        private final int side;
        private final int total;
        private final boolean fastCursor;
        private final boolean directChunkRead;
        private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        private int offsetX;
        private int offsetY;
        private int offsetZ;
        private int cursor;
        private FoliageJob(JobKey key, double range, BlockPos origin, int perTaskBudget, boolean fastCursor, boolean directChunkRead) {
            super(key, perTaskBudget);
            this.origin = origin.immutable();
            this.radius = (int) Math.ceil(range);
            this.side = radius * 2 + 1;
            this.total = side * side * side;
            this.fastCursor = fastCursor;
            this.directChunkRead = directChunkRead;
            this.offsetX = -radius;
            this.offsetY = -radius;
            this.offsetZ = -radius;
        }

        @Override public int run(ServerLevel level, int budget, long deadline) {
            Mound mound = mound(level);
            if (mound == null) { complete = true; return 0; }
            if (!FungalWorkBudget.INSTANCE.mayWork(mound, FungalWorkBudget.WorkKind.MOUND)) return 0;
            int used = 0;
            int inSphere = 0;
            int unloaded = 0;
            int cachedChunkX = Integer.MIN_VALUE;
            int cachedChunkZ = Integer.MIN_VALUE;
            LevelChunk cachedChunk = null;
            boolean cachedChunkKnown = false;
            while (used < budget && !complete) {
                if ((used & (TIME_CHECK_STRIDE - 1)) == 0 && deadlineReached(deadline)) break;
                if (cursor >= total) {
                    complete = true;
                    break;
                }
                BlockPos pos = nextPosition();
                used++;
                if (fastCursor && !withinOriginalSphere(pos)) continue;
                BlockState state;
                if (directChunkRead) {
                    int chunkX = SectionPos.blockToSectionCoord(pos.getX());
                    int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
                    if (!cachedChunkKnown || cachedChunkX != chunkX || cachedChunkZ != chunkZ) {
                        cachedChunkX = chunkX;
                        cachedChunkZ = chunkZ;
                        cachedChunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                        cachedChunkKnown = true;
                    }
                    if (cachedChunk == null) { unloaded++; continue; }
                    state = cachedChunk.getBlockState(pos);
                } else {
                    if (!level.hasChunkAt(pos)) { unloaded++; continue; }
                    state = level.getBlockState(pos);
                }
                if (!fastCursor && !withinOriginalSphere(pos)) continue;
                inSphere++;
                processStructure(level, mound, pos, state);
                mound.SpreadFoliageAndConvert(level, state, pos);
            }
            PerformanceMetrics.add("fungal.foliage.positions", used);
            PerformanceMetrics.add("fungal.foliage.in_sphere", inSphere);
            PerformanceMetrics.add("fungal.foliage.unloaded", unloaded);
            return used;
        }

        private BlockPos nextPosition() {
            if (!fastCursor) return position(cursor++);
            mutablePos.set(origin.getX() + offsetX, origin.getY() + offsetY, origin.getZ() + offsetZ);
            cursor++;
            if (++offsetX > radius) {
                offsetX = -radius;
                if (++offsetZ > radius) {
                    offsetZ = -radius;
                    offsetY++;
                }
            }
            return mutablePos;
        }

        private BlockPos position(int index) {
            int x = index % side;
            int yz = index / side;
            int z = yz % side;
            int y = yz / side;
            return origin.offset(x - radius, y - radius, z - radius);
        }

        private boolean withinOriginalSphere(BlockPos pos) {
            long dx = pos.getX() - origin.getX();
            long dy = pos.getY() - origin.getY();
            long dz = pos.getZ() - origin.getZ();
            long diameter = radius * 2L + 1L;
            return 4L * (dx * dx + dy * dy + dz * dz) < diameter * diameter;
        }

        private void processStructure(ServerLevel level, Mound mound, BlockPos pos, BlockState state) {
            if (!MoundStructureBridge.hasStructureSlot(mound)) return;
            if (Math.random() >= 0.1D || mound.getAge() < mound.getMaxAge()
                    || mound.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) <= 80D || !state.isSolidRender(level, pos)) return;
            BlockPos abovePos = pos.above();
            BlockState above = level.getBlockState(abovePos);
            if (!above.isAir()) return;
            BlockState placement = net.minecraftforge.registries.ForgeRegistries.BLOCKS.tags().getTag(STRUCTURE_TAG)
                    .getRandomElement(RandomSource.create()).orElse(Blocks.AIR).defaultBlockState();
            level.setBlock(abovePos, placement, 3);
            MoundStructureBridge.consumeStructureSlot(mound);
        }
    }
}
