package com.arxyt.sporeperformance.client.render;

import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Resource and config reload hooks for caches derived from textures and tuning values. */
@Mod.EventBusSubscriber(modid = SporePerformance.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientRenderModEvents {
    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void ignored, ResourceManager resourceManager, ProfilerFiller profiler) {
                IllusionRenderOptimizer.clear();
                AnimationLodController.clear();
                LayerAnimationDeduplicator.clear();
                OpaqueModelPartRenderer.reload(resourceManager);
                SonaInfectionFrameCache.clear();
                SonaSporeOverlayBatch.clear();
            }
        });
    }

    @SubscribeEvent
    public static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == PerformanceConfig.CLIENT_SPEC) ClientRenderLifecycle.clearAll();
    }

    private ClientRenderModEvents() {}
}
