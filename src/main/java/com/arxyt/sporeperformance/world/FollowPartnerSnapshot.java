package com.arxyt.sporeperformance.world;

import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import com.google.common.base.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Twenty-tick 64-block-cell UUID snapshots for FollowOthersGoal. The cell envelope covers every
 * native 32-block query whose owner lies inside it; exact bounds and the partner predicate are
 * reapplied for each caller. No loaded entity or level is retained by this cache.
 */
public final class FollowPartnerSnapshot {
    private static final int CELL_SIZE = 64;
    private static final Map<Key, Entry> ENTRIES = new HashMap<>();

    public static <T extends LivingEntity> List<T> query(Infected source, Level level, Class<T> type, AABB exactBounds, Predicate<LivingEntity> partnerPredicate) {
        if (!PerformanceConfig.AGGRESSIVE_GROUP_SENSING.get()) {
            return level.getEntitiesOfClass(type, exactBounds, entity -> partnerPredicate == null || partnerPredicate.apply(entity));
        }
        long now = level.getGameTime();
        int lifetime = PerformanceConfig.AGGRESSIVE_FOLLOW_SNAPSHOT_TICKS.get();
        Key key = Key.at(level.dimension(), type, source.blockPosition());
        synchronized (ENTRIES) {
            Entry entry = ENTRIES.get(key);
            // A newly occupied cell is always sampled immediately. Later refreshes wait for a
            // UUID phase, so a large group does not synchronize its 20-tick refresh scans.
            if (entry == null || (now >= entry.nextRefresh && belongsToRefreshTurn(source.getUUID(), now))) {
                entry = new Entry(now + lifetime, snapshot(level, key, type));
                ENTRIES.put(key, entry);
                ENTRIES.entrySet().removeIf(candidate -> candidate.getValue().nextRefresh + lifetime < now);
                PerformanceMetrics.increment("follow.partner_snapshot_created");
            }
            return resolve(level, type, exactBounds, partnerPredicate, entry.ids);
        }
    }

    private static <T extends LivingEntity> List<EntityRef> snapshot(Level level, Key key, Class<T> type) {
        List<EntityRef> ids = new ArrayList<>();
        for (T candidate : level.getEntitiesOfClass(type, key.coverage(), LivingEntity::isAlive)) ids.add(new EntityRef(candidate.getUUID(), candidate.getId()));
        return ids;
    }

    private static <T extends LivingEntity> List<T> resolve(Level level, Class<T> type, AABB bounds, Predicate<LivingEntity> predicate, List<EntityRef> ids) {
        List<T> result = new ArrayList<>();
        for (EntityRef id : ids) {
            Entity entity = level.getEntity(id.numericId);
            if (!type.isInstance(entity)) continue;
            T candidate = type.cast(entity);
            if (candidate.getUUID().equals(id.uuid) && candidate.isAlive() && bounds.intersects(candidate.getBoundingBox()) && (predicate == null || predicate.apply(candidate))) result.add(candidate);
        }
        return result;
    }

    private static boolean belongsToRefreshTurn(UUID id, long tick) {
        return Math.floorMod(id.hashCode(), 20) == Math.floorMod(tick, 20);
    }

    public static void clear() { synchronized (ENTRIES) { ENTRIES.clear(); } }

    private record Key(ResourceKey<Level> dimension, Class<?> type, int cellX, int cellY, int cellZ) {
        private static Key at(ResourceKey<Level> dimension, Class<?> type, BlockPos pos) {
            return new Key(dimension, type, Math.floorDiv(pos.getX(), CELL_SIZE), Math.floorDiv(pos.getY(), CELL_SIZE), Math.floorDiv(pos.getZ(), CELL_SIZE));
        }
        private AABB coverage() {
            double minX = cellX * (double) CELL_SIZE - 32.0D;
            double minY = cellY * (double) CELL_SIZE - 32.0D;
            double minZ = cellZ * (double) CELL_SIZE - 32.0D;
            return new AABB(minX, minY, minZ, minX + 128.0D, minY + 128.0D, minZ + 128.0D);
        }
    }
    private record EntityRef(UUID uuid, int numericId) {}
    private record Entry(long nextRefresh, List<EntityRef> ids) {}
    private FollowPartnerSnapshot() {}
}
