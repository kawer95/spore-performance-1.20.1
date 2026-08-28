package com.arxyt.sporeperformance.compat;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.arxyt.sporeperformance.SporePerformance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Reuses Sona's immutable-within-a-tick global infection enablement check. */
@Mod.EventBusSubscriber(modid = SporePerformance.MODID)
public final class SonaCanChunkTickCache {
    private static final Map<Level, Entry> ENTRIES = new ConcurrentHashMap<>();

    public static Boolean get(Level level) {
        if (!PerformanceConfig.SAFE_SONA_CAN_CHUNK_CACHE.get()) return null;
        Entry entry = ENTRIES.get(level);
        if (entry == null || entry.gameTime != level.getGameTime()) return null;
        PerformanceMetrics.increment("sona.can_chunk_cache_hit");
        return entry.value;
    }

    public static void put(Level level, boolean value) {
        if (PerformanceConfig.SAFE_SONA_CAN_CHUNK_CACHE.get()) {
            ENTRIES.put(level, new Entry(level.getGameTime(), value));
        }
    }

    public static void clear() {
        ENTRIES.clear();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) ENTRIES.remove(level);
    }

    private record Entry(long gameTime, boolean value) {}

    private SonaCanChunkTickCache() {}
}
