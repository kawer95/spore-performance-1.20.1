package com.arxyt.sporeperformance.world;

import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import com.arxyt.sporeperformance.diagnostics.DebugTrace;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Per-dimension ceiling for loaded Spore workers.  New entities are rejected before they are
 * inserted into the level; saved entities remain loadable so enabling a cap never damages a world.
 */
public final class SporePopulationLimiter {
    public static final SporePopulationLimiter INSTANCE = new SporePopulationLimiter();

    private static final String SPORE_NAMESPACE = "spore";
    private static final String MOUND = "mound";
    private static final String TENDRIL = "tendril";

    private final SporePopulationCounter<ResourceKey<Level>> counter = new SporePopulationCounter<>();
    private final CalamityPopulationCounter<ResourceKey<Level>> calamityCounter = new CalamityPopulationCounter<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public synchronized void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getLevel() instanceof ServerLevel level)) return;

        SporePopulationCounter.Category category = categoryOf(event.getEntity());
        if (!category.isTracked()) return;

        SporePopulationCounter.Rejection rejection = counter.track(
                level.dimension(),
                event.getEntity().getUUID(),
                category,
                limits(),
                !event.loadedFromDisk());
        if (rejection != SporePopulationCounter.Rejection.NONE) {
            event.setCanceled(true);
            PerformanceMetrics.increment("population.rejected." + rejection.name().toLowerCase(java.util.Locale.ROOT));
            if (DebugTrace.enabled(DebugTrace.Category.BACKGROUND))
                DebugTrace.event(DebugTrace.Category.BACKGROUND, level, DebugTrace.trace(event.getEntity()), event.getEntity(),
                        "population_rejected", "reason=" + rejection + ",loadedFromDisk=" + event.loadedFromDisk());
            return;
        }

        String calamityType = calamityTypeOf(event.getEntity());
        if (calamityType == null) return;

        CalamityPopulationCounter.Rejection calamityRejection = calamityCounter.track(
                level.dimension(), event.getEntity().getUUID(), calamityType, calamityLimits(), !event.loadedFromDisk());
        if (calamityRejection == CalamityPopulationCounter.Rejection.NONE) return;

        // The general counter was updated before the calamity-specific check. Roll it back so a
        // rejected boss never consumes one slot from the unrelated fungal-unit population cap.
        counter.untrack(event.getEntity().getUUID());
        event.setCanceled(true);
        PerformanceMetrics.increment("population.rejected.calamity_"
                + calamityRejection.name().toLowerCase(java.util.Locale.ROOT));
        if (DebugTrace.enabled(DebugTrace.Category.BACKGROUND))
            DebugTrace.event(DebugTrace.Category.BACKGROUND, level, DebugTrace.trace(event.getEntity()), event.getEntity(),
                    "calamity_population_rejected", "reason=" + calamityRejection + ",type=" + calamityType
                            + ",loadedFromDisk=" + event.loadedFromDisk());
    }

    @SubscribeEvent
    public synchronized void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide) {
            counter.untrack(event.getEntity().getUUID());
            calamityCounter.untrack(event.getEntity().getUUID());
        }
    }

    public synchronized List<String> statusLines() {
        SporePopulationCounter.Limits limits = limits();
        List<String> lines = new ArrayList<>();
        lines.add("Population caps (loaded/per dimension): Spore units=" + limits.fungalUnits()
                + ", Mounds=" + limits.mounds() + ", Tendrils=" + limits.tendrils() + " (0 = unlimited)");
        CalamityPopulationCounter.Limits calamityLimits = calamityLimits();
        lines.add("Calamity caps (loaded/per dimension): total=" + calamityLimits.total()
                + ", perType=" + calamityLimits.perType() + " (-1 = unlimited)");

        List<Map.Entry<ResourceKey<Level>, SporePopulationCounter.Snapshot>> snapshots = new ArrayList<>(counter.snapshots().entrySet());
        snapshots.sort(Comparator.comparing(entry -> entry.getKey().location().toString()));
        for (Map.Entry<ResourceKey<Level>, SporePopulationCounter.Snapshot> entry : snapshots) {
            SporePopulationCounter.Snapshot snapshot = entry.getValue();
            lines.add("Population " + entry.getKey().location() + ": units=" + snapshot.fungalUnits()
                    + ", mounds=" + snapshot.mounds() + ", tendrils=" + snapshot.tendrils());
        }
        List<Map.Entry<ResourceKey<Level>, CalamityPopulationCounter.Snapshot>> calamitySnapshots =
                new ArrayList<>(calamityCounter.snapshots().entrySet());
        calamitySnapshots.sort(Comparator.comparing(entry -> entry.getKey().location().toString()));
        for (Map.Entry<ResourceKey<Level>, CalamityPopulationCounter.Snapshot> entry : calamitySnapshots) {
            CalamityPopulationCounter.Snapshot snapshot = entry.getValue();
            lines.add("Calamities " + entry.getKey().location() + ": total=" + snapshot.total()
                    + ", perType=" + snapshot.perType());
        }
        return lines;
    }

    public synchronized void clear() {
        counter.clear();
        calamityCounter.clear();
    }

    private static SporePopulationCounter.Limits limits() {
        return new SporePopulationCounter.Limits(
                PerformanceConfig.LIMIT_FUNGAL_UNITS_PER_DIMENSION.get(),
                PerformanceConfig.LIMIT_MOUNDS_PER_DIMENSION.get(),
                PerformanceConfig.LIMIT_TENDRILS_PER_DIMENSION.get());
    }

    private static CalamityPopulationCounter.Limits calamityLimits() {
        return new CalamityPopulationCounter.Limits(
                PerformanceConfig.LIMIT_CALAMITY_TOTAL_PER_DIMENSION.get(),
                PerformanceConfig.LIMIT_CALAMITY_PER_TYPE_PER_DIMENSION.get());
    }

    private static SporePopulationCounter.Category categoryOf(Entity entity) {
        ResourceLocation typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (typeKey == null || !SPORE_NAMESPACE.equals(typeKey.getNamespace())) {
            return SporePopulationCounter.Category.NONE;
        }
        String path = typeKey.getPath();
        boolean mound = MOUND.equals(path);
        boolean tendril = TENDRIL.equals(path);
        boolean fungalUnit = entity instanceof Mob;
        return fungalUnit || mound || tendril
                ? new SporePopulationCounter.Category(fungalUnit, mound, tendril)
                : SporePopulationCounter.Category.NONE;
    }

    private static String calamityTypeOf(Entity entity) {
        if (!(entity instanceof Calamity)) return null;
        ResourceLocation typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return typeKey == null || !SPORE_NAMESPACE.equals(typeKey.getNamespace()) ? null : typeKey.toString();
    }

    private SporePopulationLimiter() {}
}
