package com.arxyt.sporeperformance.world;

import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
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
import java.util.function.Predicate;

/** Five-tick, spatially scoped UUID snapshots for repeated linked-infected target broadcasts. */
public final class GroupSensingCache {
    private static final long TTL = 5L;
    private static final int CELL_SIZE = 32;
    private static final Map<Key, Entry> ENTRIES = new HashMap<>();

    public static <T extends LivingEntity> List<T> query(Infected source, Level level, Class<T> type, AABB bounds, Predicate<? super T> filter) {
        if (!PerformanceConfig.AGGRESSIVE_GROUP_SENSING.get() || source.getTarget() == null) {
            return level.getEntitiesOfClass(type, bounds, filter);
        }
        Key key = Key.at(level.dimension(), source.getTarget().getUUID(), type, source.blockPosition());
        long now = level.getGameTime();
        synchronized (ENTRIES) {
            Entry entry = ENTRIES.get(key);
            if (entry == null || entry.until < now) {
                // The wider cell envelope contains every native 32-block scan made by a
                // source in this cell. Exact bounds and the native predicate are reapplied
                // below, so cached candidates cannot leak across the caller's range.
                List<EntityRef> ids = new ArrayList<>();
                for (T candidate : level.getEntitiesOfClass(type, key.coverage(), filter)) ids.add(new EntityRef(candidate.getUUID(), candidate.getId()));
                entry = new Entry(now + TTL, ids);
                ENTRIES.put(key, entry);
                ENTRIES.entrySet().removeIf(candidate -> candidate.getValue().until < now);
                return resolve(level, type, bounds, filter, ids);
            }
            PerformanceMetrics.increment("group.shared_neighbour_query");
            return resolve(level, type, bounds, filter, entry.ids);
        }
    }

    private static <T extends LivingEntity> List<T> resolve(Level level, Class<T> type, AABB bounds, Predicate<? super T> filter, List<EntityRef> ids) {
        List<T> result = new ArrayList<>();
        for (EntityRef id : ids) {
            Entity entity = level.getEntity(id.numericId);
            if (type.isInstance(entity)) {
                T candidate = type.cast(entity);
                if (candidate.getUUID().equals(id.uuid) && candidate.isAlive() && bounds.intersects(candidate.getBoundingBox()) && filter.test(candidate)) result.add(candidate);
            }
        }
        return result;
    }

    public static void clear() { synchronized (ENTRIES) { ENTRIES.clear(); } }

    private record Key(ResourceKey<Level> dimension, UUID target, Class<?> type, int cellX, int cellY, int cellZ) {
        private static Key at(ResourceKey<Level> dimension, UUID target, Class<?> type, net.minecraft.core.BlockPos pos) {
            return new Key(dimension, target, type, Math.floorDiv(pos.getX(), CELL_SIZE), Math.floorDiv(pos.getY(), CELL_SIZE), Math.floorDiv(pos.getZ(), CELL_SIZE));
        }
        private AABB coverage() {
            double minX = cellX * (double) CELL_SIZE - 32.0D;
            double minY = cellY * (double) CELL_SIZE - 32.0D;
            double minZ = cellZ * (double) CELL_SIZE - 32.0D;
            return new AABB(minX, minY, minZ, minX + 96.0D, minY + 96.0D, minZ + 96.0D);
        }
    }
    private record EntityRef(UUID uuid, int numericId) {}
    private record Entry(long until, List<EntityRef> ids) {}
    private GroupSensingCache() {}
}
