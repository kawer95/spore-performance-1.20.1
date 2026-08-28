package com.arxyt.sporeperformance.client;

import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.registry.PerformanceEntities;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = SporePerformance.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PerformanceClientModEvents {
    /** Opens the three-preset screen; users can change this key in Controls. */
    public static final KeyMapping OPEN_PROFILE_SCREEN = new KeyMapping(
            "key.spore_performance.profile_screen", InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P, "key.categories.spore_performance");

    private PerformanceClientModEvents() {}

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_PROFILE_SCREEN);
    }

    @SubscribeEvent public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(PerformanceEntities.STAHL_RISING_BLOCK.get(), StahlRisingBlockRenderer::new);
    }
}
