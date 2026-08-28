package com.arxyt.sporeperformance.world;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** One broad world query per touched chunk/tick; exact AABB and predicates are always reapplied. */
public final class ProjectileBroadphaseCache {
    private static final Map<Level, TickState> LEVELS = new IdentityHashMap<>();

    public static List<Entity> query(Level level, Entity source, AABB box, Predicate<? super Entity> predicate) {
        if (!PerformanceConfig.SAFE_SPORE_PROJECTILE_BROADPHASE.get() || !isSporeProjectile(source)) {
            return level.getEntities(source, box, predicate);
        }
        TickState state = LEVELS.computeIfAbsent(level, ignored -> new TickState());
        long now = level.getGameTime();
        if (state.gameTime != now) { state.gameTime = now; state.chunks.clear(); }
        int minX = Mth.floor(box.minX) >> 4, maxX = Mth.floor(box.maxX) >> 4;
        int minZ = Mth.floor(box.minZ) >> 4, maxZ = Mth.floor(box.maxZ) >> 4;
        Set<Entity> candidates = new LinkedHashSet<>();
        for (int x = minX; x <= maxX; ++x) for (int z = minZ; z <= maxZ; ++z) {
            long key = ChunkPos.asLong(x, z);
            List<Entity> bucket = state.chunks.get(key);
            if (bucket == null) {
                AABB chunkBox = new AABB(x << 4, level.getMinBuildHeight(), z << 4,
                        (x << 4) + 16, level.getMaxBuildHeight(), (z << 4) + 16);
                // Cache must not depend on whichever projectile populated it first. Exclude the
                // current source only after lookup so later projectiles see exactly their own set.
                bucket = level.getEntities((Entity) null, chunkBox, entity -> true);
                state.chunks.put(key, bucket);
                PerformanceMetrics.increment("projectile.broadphase_world_queries");
            } else PerformanceMetrics.increment("projectile.broadphase_cache_hits");
            candidates.addAll(bucket);
        }
        List<Entity> result = new ArrayList<>();
        for (Entity entity : candidates) if (entity != source && !entity.isRemoved()
                && entity.getBoundingBox().intersects(box) && predicate.test(entity)) result.add(entity);
        return result;
    }

    public static void clear() { LEVELS.clear(); }
    private static boolean isSporeProjectile(Entity entity) {
        if (!(entity instanceof Projectile)) return false;
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key != null && "spore".equals(key.getNamespace());
    }
    private static final class TickState {
        private long gameTime = Long.MIN_VALUE;
        private final Map<Long, List<Entity>> chunks = new HashMap<>();
    }
    private ProjectileBroadphaseCache() {}
}
