package com.arxyt.sporeperformance.client.render;

import com.arxyt.sporeperformance.SporePerformance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Clears client-only caches at entity and world lifecycle boundaries. */
@Mod.EventBusSubscriber(modid = SporePerformance.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientRenderLifecycle {
    @SubscribeEvent
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) AnimationLodController.removeEntity(event.getEntity().getId());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clearAll();
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clearAll();
    }

    public static void clearAll() {
        IllusionRenderOptimizer.clear();
        AnimationLodController.clear();
        LayerAnimationDeduplicator.clear();
        OpaqueModelPartRenderer.clear();
        SonaInfectionFrameCache.clear();
        SonaSporeOverlayBatch.clear();
    }

    private ClientRenderLifecycle() {}
}
