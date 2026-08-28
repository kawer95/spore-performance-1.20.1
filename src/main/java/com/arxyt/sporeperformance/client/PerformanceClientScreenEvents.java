package com.arxyt.sporeperformance.client;

import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.client.gui.OptimizationProfileScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Adds a visible entry to the pause menu in addition to the configurable keybind. */
@Mod.EventBusSubscriber(modid = SporePerformance.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PerformanceClientScreenEvents {
    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof PauseScreen screen)) return;
        int buttonWidth = 200;
        int x = screen.width / 2 - buttonWidth / 2;
        // The vanilla pause buttons occupy the upper half; keep this entry at
        // the bottom so it remains visible with common menu extensions.
        int y = Math.max(0, screen.height - 28);
        event.addListener(Button.builder(net.minecraft.network.chat.Component.literal("Spore 优化预设"),
                        button -> OptimizationProfileScreen.open())
                .bounds(x, y, buttonWidth, 20).build());
    }

    private PerformanceClientScreenEvents() {}
}
