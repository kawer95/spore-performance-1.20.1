package com.arxyt.sporeperformance.compat;

import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/** Loaded-only player lookup and wake decisions for Touhou Little Maid power points. */
public final class PowerPointTickRuntime {
    public static boolean enabled() {
        return PerformanceConfig.COMPAT_TOUHOU_POWER_POINT_OPTIMIZATION.get();
    }

    public static Player nearestPlayer(Entity point, double radius) {
        if (!(point.level() instanceof ServerLevel level)) return null;
        Player nearest = null;
        double best = radius * radius;
        for (Player candidate : FungalAiRuntime.query(level, point,
                point.getBoundingBox().inflate(radius), Player.class)) {
            if (candidate.isSpectator()) continue;
            double distance = point.distanceToSqr(candidate);
            if (distance < best) {
                best = distance;
                nearest = candidate;
            }
        }
        PerformanceMetrics.increment("touhou.power_point.player_index_queries");
        return nearest;
    }

    public static boolean shouldSkipPhysics(Entity point, int tickCount) {
        if (!enabled() || point.level().isClientSide || !point.onGround()
                || point.isInWaterOrBubble() || point.isInLava()
                || point.getDeltaMovement().lengthSqr() > 1.0E-6D) return false;
        if (nearestPlayer(point, 8.0D) != null) return false;
        int interval = Math.max(1, PerformanceConfig.COMPAT_TOUHOU_GROUNDED_PHYSICS_INTERVAL.get());
        boolean skip = Math.floorMod(tickCount + point.getId(), interval) != 0;
        if (skip) PerformanceMetrics.increment("touhou.power_point.physics_reused");
        return skip;
    }

    private PowerPointTickRuntime() {}
}
