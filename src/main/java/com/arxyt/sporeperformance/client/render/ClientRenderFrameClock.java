package com.arxyt.sporeperformance.client.render;

import com.arxyt.sporeperformance.SporePerformance;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Monotonic client frame number used to phase animation updates across entities. */
@Mod.EventBusSubscriber(modid = SporePerformance.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientRenderFrameClock {
    private static long frame;

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) frame++;
    }

    public static long frame() {
        return frame;
    }

    private ClientRenderFrameClock() {}
}
