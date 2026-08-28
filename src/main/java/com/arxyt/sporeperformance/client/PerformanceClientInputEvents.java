package com.arxyt.sporeperformance.client;

import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.client.gui.OptimizationProfileScreen;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Handles the client-only profile-screen key without loading GUI classes on a server. */
@Mod.EventBusSubscriber(modid = SporePerformance.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PerformanceClientInputEvents {
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !PerformanceClientModEvents.OPEN_PROFILE_SCREEN.consumeClick()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null || minecraft.screen instanceof OptimizationProfileScreen) {
            OptimizationProfileScreen.open();
        }
    }

    private PerformanceClientInputEvents() {}
}
