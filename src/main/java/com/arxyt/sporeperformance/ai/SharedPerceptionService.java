package com.arxyt.sporeperformance.ai;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import com.arxyt.sporeperformance.diagnostics.DebugTrace;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One-tick candidate frames shared by observers in the same 32-block cell. */
public final class SharedPerceptionService {
    private static final int CELL = 32;
    private final FungalEntityIndex index;
    private final Map<FrameKey, List<LivingEntity>> frames = new HashMap<>();
    private long frameTick = Long.MIN_VALUE;

    SharedPerceptionService(FungalEntityIndex index) { this.index = index; }

    public <T extends LivingEntity> List<T> candidates(long tick, Entity observer, AABB exactBounds, Class<T> type) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get() || !PerformanceConfig.REFACTOR_SHARED_PERCEPTION.get()) {
            return index.query(exactBounds, type, observer);
        }
        beginTick(tick);
        int cellX = Mth.floor(observer.getX()) >> 5;
        int cellY = Mth.floor(observer.getY()) >> 5;
        int cellZ = Mth.floor(observer.getZ()) >> 5;
        int horizontal = roundRange(Math.max(exactBounds.getXsize(), exactBounds.getZsize()) * 0.5D);
        int vertical = roundRange(exactBounds.getYsize() * 0.5D);
        FrameKey key = new FrameKey(cellX, cellY, cellZ, horizontal, vertical, type);
        List<LivingEntity> frame = frames.get(key);
        if (frame == null) {
            AABB coverage = coverage(key);
            @SuppressWarnings({"rawtypes", "unchecked"})
            List<LivingEntity> typed = (List) index.query(coverage, (Class) type, null);
            frame = new ArrayList<>(typed);
            frames.put(key, frame);
            PerformanceMetrics.increment("ai_refactor.perception.frames_built");
            PerformanceMetrics.add("ai_refactor.perception.frame_candidates", frame.size());
            if (DebugTrace.enabled(DebugTrace.Category.PERCEPTION) && observer.level() instanceof ServerLevel level)
                DebugTrace.event(DebugTrace.Category.PERCEPTION, level, DebugTrace.trace(observer), observer,
                        "frame_built", "cell=" + cellX + "," + cellY + "," + cellZ + ",type=" + type.getName()
                                + ",candidates=" + frame.size());
        } else {
            PerformanceMetrics.increment("ai_refactor.perception.frames_reused");
            if (DebugTrace.enabled(DebugTrace.Category.PERCEPTION) && observer.level() instanceof ServerLevel level)
                DebugTrace.event(DebugTrace.Category.PERCEPTION, level, DebugTrace.trace(observer), observer,
                        "frame_reused", "type=" + type.getName() + ",candidates=" + frame.size());
        }
        List<T> result = new ArrayList<>();
        for (LivingEntity candidate : frame) {
            if (candidate != observer && type.isInstance(candidate) && candidate.isAlive()
                    && exactBounds.intersects(candidate.getBoundingBox())) result.add(type.cast(candidate));
        }
        return result;
    }

    public <T extends LivingEntity> T nearest(Mob observer, AABB bounds, Class<T> type, TargetingConditions conditions) {
        T nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (T candidate : candidates(observer.level().getGameTime(), observer, bounds, type)) {
            if (!conditions.test(observer, candidate)) continue;
            double distance = observer.distanceToSqr(candidate);
            if (distance < nearestDistance || distance == nearestDistance && nearest != null && candidate.getId() < nearest.getId()) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }
        if (DebugTrace.enabled(DebugTrace.Category.PERCEPTION) && observer.level() instanceof ServerLevel level)
            DebugTrace.event(DebugTrace.Category.PERCEPTION, level, DebugTrace.trace(observer), observer,
                    "nearest_result", "type=" + type.getName() + ",target=" + (nearest == null ? "" : nearest.getUUID())
                            + ",distanceSqr=" + nearestDistance);
        return nearest;
    }

    public void beginTick(long tick) {
        if (frameTick == tick) return;
        frameTick = tick;
        frames.clear();
    }

    public int frameCount() { return frames.size(); }
    public void clear() { frames.clear(); frameTick = Long.MIN_VALUE; }

    private static int roundRange(double value) { return Math.max(16, Mth.ceil(value / 16.0D) * 16); }
    private static AABB coverage(FrameKey key) {
        double minX = key.cellX * (double) CELL - key.horizontal;
        double minY = key.cellY * (double) CELL - key.vertical;
        double minZ = key.cellZ * (double) CELL - key.horizontal;
        return new AABB(minX, minY, minZ, minX + CELL + key.horizontal * 2.0D,
                minY + CELL + key.vertical * 2.0D, minZ + CELL + key.horizontal * 2.0D);
    }

    private record FrameKey(int cellX, int cellY, int cellZ, int horizontal, int vertical, Class<?> type) {}
}
