package com.arxyt.sporeperformance.compat;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** UUID-to-dimension hints. They contain no entity or level reference and are discarded on unload. */
public final class DimensionEntityIndex {
    public static final DimensionEntityIndex INSTANCE = new DimensionEntityIndex();
    private final Map<UUID, ResourceKey<Level>> dimensions = new HashMap<>();

    @SubscribeEvent
    public synchronized void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide) dimensions.put(event.getEntity().getUUID(), event.getLevel().dimension());
    }

    @SubscribeEvent
    public synchronized void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide) dimensions.remove(event.getEntity().getUUID());
    }

    public synchronized Entity getOrNull(ServerLevel requestedLevel, UUID id) {
        if (!PerformanceConfig.SAFE_SPORESRP_DIMENSION_GUARDS.get() || !OptionalCompatProbe.sporesrpReady()) {
            return requestedLevel.getEntity(id);
        }
        ResourceKey<Level> dimension = dimensions.get(id);
        if (dimension != null && !dimension.equals(requestedLevel.dimension())) {
            PerformanceMetrics.increment("sporesrp.cross_dimension_lookup_skipped");
            return null;
        }
        Entity entity = requestedLevel.getEntity(id);
        if (entity != null) dimensions.put(id, requestedLevel.dimension());
        return entity;
    }

    public synchronized ResourceKey<Level> knownDimension(UUID id) { return dimensions.get(id); }

    public synchronized void clear() { dimensions.clear(); }
    private DimensionEntityIndex() {}
}
