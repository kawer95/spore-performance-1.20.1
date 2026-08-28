package com.arxyt.sporeperformance.compat;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Small, server-thread-owned cache for AI Fix's expensive ballistic LOS calculation. */
public final class HowitzerLosCache {
    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();

    public static Boolean find(Entity shooter, Entity target) {
        if (!OptionalCompatProbe.aiFixHowitzerReady()) return null;
        long now = shooter.level().getGameTime();
        Entry entry = ENTRIES.get(shooter.getUUID());
        if (entry == null || !entry.target.equals(target.getUUID()) || entry.targetPosition.distanceToSqr(target.position()) > 2.25D) return null;
        int ttl = PerformanceConfig.AGGRESSIVE_HOWITZER_CACHE.get()
                ? PerformanceConfig.AGGRESSIVE_HOWITZER_CACHE_TICKS.get() : 0;
        if (entry.tick + ttl < now) return null;
        PerformanceMetrics.increment("howitzer.los_cache_hit");
        return entry.value;
    }

    public static void put(Entity shooter, Entity target, boolean value) {
        if (!OptionalCompatProbe.aiFixHowitzerReady() || !(target instanceof LivingEntity)) return;
        ENTRIES.put(shooter.getUUID(), new Entry(target.getUUID(), target.position(), shooter.level().getGameTime(), value));
        if (ENTRIES.size() > 4096) ENTRIES.entrySet().removeIf(entry -> entry.getValue().tick + 40 < shooter.level().getGameTime());
    }

    public static void clear() { ENTRIES.clear(); }

    private record Entry(UUID target, net.minecraft.world.phys.Vec3 targetPosition, long tick, boolean value) {}
    private HowitzerLosCache() {}
}
