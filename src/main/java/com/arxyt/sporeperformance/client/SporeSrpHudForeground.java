package com.arxyt.sporeperformance.client;

import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Relocates sporesrp's HUD to at most one deterministic client render stage. By default the HUD is
 * hidden during ordinary gameplay and is drawn only after an open Screen, where it stays above
 * blur effects. The optional target method is resolved once and cached; missing or drifted
 * sporesrp signatures disable the adapter without hiding the original HUD. A thread-local bypass
 * lets the adapter invoke the transformed original method without recursively allowing Forge's
 * repeated overlay callbacks.
 */
@Mod.EventBusSubscriber(modid = SporePerformance.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SporeSrpHudForeground {
    private static final String HUD_CLASS = "com.maha_fish.sporesrp.client.HUDOverlay";
    private static final ThreadLocal<Boolean> RELOCATED_INVOCATION = ThreadLocal.withInitial(() -> false);
    private static volatile MethodHandle renderHandle = resolveRenderHandle();
    private static volatile boolean invocationFailureLogged;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onOverlayPost(RenderGuiOverlayEvent.Post event) {
        if (!isRelocationActive() || !VanillaGuiOverlay.HOTBAR.id().equals(event.getOverlay().id())) return;
        Minecraft minecraft = Minecraft.getInstance();
        boolean screenOpen = minecraft.screen != null;
        if (!HudRenderStagePolicy.useGameplayOverlayStage(screenOpen,
                PerformanceConfig.CLIENT_SPORESRP_HUD_ABOVE_SCREENS.get(),
                PerformanceConfig.CLIENT_SPORESRP_HUD_IN_GAMEPLAY.get())) return;
        invokeOriginal(event.getGuiGraphics(), event.getPartialTick());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        if (!isRelocationActive()) return;
        Minecraft minecraft = Minecraft.getInstance();
        boolean screenOpen = minecraft.screen != null;
        if (minecraft.level == null || minecraft.player == null
                || !HudRenderStagePolicy.useScreenForegroundStage(screenOpen, PerformanceConfig.CLIENT_SPORESRP_HUD_ABOVE_SCREENS.get())) return;
        invokeOriginal(event.getGuiGraphics(), event.getPartialTick());
    }

    /** Used by the optional Mixin to preserve direct adapter calls while cancelling event-bus calls. */
    public static boolean shouldCancelOriginal() {
        return isRelocationActive() && !RELOCATED_INVOCATION.get();
    }

    private static boolean isRelocationActive() {
        return renderHandle != null && PerformanceConfig.COMPAT_SPORESRP_AUTO_DETECT.get()
                && PerformanceConfig.CLIENT_SPORESRP_HUD_HOTBAR.get();
    }

    private static void invokeOriginal(GuiGraphics graphics, float partialTick) {
        MethodHandle handle = renderHandle;
        if (handle == null) return;
        RenderGuiOverlayEvent.Post relocatedEvent = new RenderGuiOverlayEvent.Post(
                Minecraft.getInstance().getWindow(), graphics, partialTick, VanillaGuiOverlay.HOTBAR.type());
        RELOCATED_INVOCATION.set(true);
        try {
            handle.invokeExact(relocatedEvent);
        } catch (Throwable throwable) {
            renderHandle = null;
            if (!invocationFailureLogged) {
                invocationFailureLogged = true;
                SporePerformance.LOGGER.warn("sporesrp HUD relocation failed; preserving fail-closed client behavior", throwable);
            }
        } finally {
            RELOCATED_INVOCATION.remove();
        }
    }

    private static MethodHandle resolveRenderHandle() {
        if (!ModList.get().isLoaded("sporesrp")) return null;
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> hud = Class.forName(HUD_CLASS, false, loader);
            return MethodHandles.publicLookup().findStatic(hud, "onRenderGui",
                    MethodType.methodType(void.class, RenderGuiOverlayEvent.Post.class));
        } catch (ReflectiveOperationException | LinkageError exception) {
            SporePerformance.LOGGER.warn("sporesrp HUD signature is incompatible; relocation disabled", exception);
            return null;
        }
    }

    private SporeSrpHudForeground() {}
}
