package com.arxyt.sporeperformance;

import com.arxyt.sporeperformance.command.SporePerformanceCommands;
import com.arxyt.sporeperformance.compat.OptionalCompatProbe;
import com.arxyt.sporeperformance.compat.DimensionEntityIndex;
import com.arxyt.sporeperformance.compat.HowitzerLosCache;
import com.arxyt.sporeperformance.compat.HowitzerTrajectoryBudget;
import com.arxyt.sporeperformance.compat.SporeSrpBlockBudget;
import com.arxyt.sporeperformance.compat.SporeSrpBackgroundScheduler;
import com.arxyt.sporeperformance.compat.SporeSrpStagger;
import com.arxyt.sporeperformance.compat.SonaCanChunkTickCache;
import com.arxyt.sporeperformance.compat.MoundStructureBridge;
import com.arxyt.sporeperformance.compat.TaczDamageBypass;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.scheduler.FungalWorkScheduler;
import com.arxyt.sporeperformance.world.StructureBlockIndex;
import com.arxyt.sporeperformance.world.RemoteIdleAiController;
import com.arxyt.sporeperformance.world.InfectionConversionCache;
import com.arxyt.sporeperformance.world.GroupSensingCache;
import com.arxyt.sporeperformance.world.FollowPartnerSnapshot;
import com.arxyt.sporeperformance.world.SporePopulationLimiter;
import com.arxyt.sporeperformance.world.ItemMergeCoordinator;
import com.arxyt.sporeperformance.world.LivingEntitySpatialIndex;
import com.arxyt.sporeperformance.world.FungalWorkBudget;
import com.arxyt.sporeperformance.world.TargetAcquisitionController;
import com.arxyt.sporeperformance.runtime.GeneralPathBackoff;
import com.arxyt.sporeperformance.world.ProjectileBroadphaseCache;
import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.arxyt.sporeperformance.registry.PerformanceEntities;
import com.arxyt.sporeperformance.diagnostics.DebugTrace;
import com.arxyt.sporeperformance.diagnostics.CalamityTrace;
import com.arxyt.sporeperformance.diagnostics.LoadedEntityCensus;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Mod bootstrap. This class only wires lifecycle services; it intentionally owns no world state.
 */
@Mod(SporePerformance.MODID)
public final class SporePerformance {
    public static final String MODID = "spore_performance";
    public static final Logger LOGGER = LogUtils.getLogger();

    public SporePerformance() {
        PerformanceEntities.TYPES.register(FMLJavaModLoadingContext.get().getModEventBus());
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, PerformanceConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, PerformanceConfig.CLIENT_SPEC);
        MinecraftForge.EVENT_BUS.register(FungalWorkScheduler.INSTANCE);
        MinecraftForge.EVENT_BUS.register(StructureBlockIndex.INSTANCE);
        MinecraftForge.EVENT_BUS.register(DimensionEntityIndex.INSTANCE);
        MinecraftForge.EVENT_BUS.register(SporePopulationLimiter.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ItemMergeCoordinator.INSTANCE);
        MinecraftForge.EVENT_BUS.register(LoadedEntityCensus.INSTANCE);
        MinecraftForge.EVENT_BUS.register(FungalAiRuntime.INSTANCE);
        MinecraftForge.EVENT_BUS.register(FungalWorkBudget.INSTANCE);
        // This service has a direct optional sporesrp helper reference.  Do not construct it
        // on installations that intentionally run Spore without sporesrp.
        if (ModList.get().isLoaded("sporesrp")) MinecraftForge.EVENT_BUS.register(SporeSrpBackgroundScheduler.INSTANCE);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopped);
    }

    private void onServerStarting(ServerStartingEvent event) {
        // Forge's config Loading event may run before ConfigValue#get is bound
        // on a client/dev launch. Refresh immutable snapshots at the first
        // server lifecycle point where common specs are guaranteed ready.
        InfectionConversionCache.refresh();
        RemoteIdleAiController.refreshFromConfig();
        OptionalCompatProbe.refresh();
        TaczDamageBypass.refresh();
        MoundStructureBridge.initialize();
        SporePerformanceCommands.register(event.getServer().getCommands().getDispatcher());
        LOGGER.info("Spore Performance compatibility: {}", OptionalCompatProbe.summary());
        if (DebugTrace.enabled(DebugTrace.Category.COMPAT))
            DebugTrace.state(DebugTrace.Category.COMPAT, event.getServer().overworld(), 0L, null,
                    "server_started", OptionalCompatProbe.summary());
    }

    private void onServerStopped(ServerStoppedEvent event) {
        FungalWorkScheduler.INSTANCE.clear();
        StructureBlockIndex.INSTANCE.clear();
        DimensionEntityIndex.INSTANCE.clear();
        HowitzerLosCache.clear();
        HowitzerTrajectoryBudget.clear();
        SporeSrpBlockBudget.clear();
        if (ModList.get().isLoaded("sporesrp")) SporeSrpBackgroundScheduler.INSTANCE.clear();
        SporeSrpStagger.clear();
        RemoteIdleAiController.clear();
        GroupSensingCache.clear();
        FollowPartnerSnapshot.clear();
        SporePopulationLimiter.INSTANCE.clear();
        ItemMergeCoordinator.INSTANCE.clear();
        LoadedEntityCensus.INSTANCE.clear();
        LivingEntitySpatialIndex.INSTANCE.clear();
        FungalAiRuntime.INSTANCE.clear();
        FungalWorkBudget.INSTANCE.clear();
        TargetAcquisitionController.clear();
        GeneralPathBackoff.clear();
        ProjectileBroadphaseCache.clear();
        SonaCanChunkTickCache.clear();
        TaczDamageBypass.clear();
        DebugTrace.close();
        CalamityTrace.INSTANCE.close();
    }
}
