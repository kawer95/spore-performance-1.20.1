package com.arxyt.sporeperformance.world;

import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/** Compatibility facade retained for older optimization mixins; storage is owned by FungalAiRuntime. */
public final class LivingEntitySpatialIndex {
    public static final LivingEntitySpatialIndex INSTANCE = new LivingEntitySpatialIndex();

    @SuppressWarnings({"rawtypes", "unchecked"})
    public List<LivingEntity> query(ServerLevel level, AABB area, Class<?> type, Entity except) {
        List result = FungalAiRuntime.query(level, except, area, (Class<? extends LivingEntity>) type);
        PerformanceMetrics.increment("ai_refactor.perception.compat_queries");
        PerformanceMetrics.add("ai_refactor.perception.compat_candidates", result.size());
        return result;
    }

    public boolean hasPlayerWithin(ServerLevel level, LivingEntity source, double distance) {
        double maximum = distance * distance;
        for (net.minecraft.server.level.ServerPlayer player :
                FungalAiRuntime.query(level, source, source.getBoundingBox().inflate(distance),
                        net.minecraft.server.level.ServerPlayer.class)) {
            if (!player.isSpectator() && player.distanceToSqr(source) <= maximum) return true;
        }
        return false;
    }

    public void clear() { /* Runtime owns lifecycle. */ }
    private LivingEntitySpatialIndex() {}
}
