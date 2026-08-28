package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.client.SporeSrpHudForeground;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancels sporesrp's unfiltered per-overlay callback while the foreground adapter is available. */
@Mixin(targets = "com.maha_fish.sporesrp.client.HUDOverlay", remap = false)
public abstract class OptionalSporeSrpHudMixin {
    @Inject(method = "onRenderGui", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sporePerformance$relocateHud(RenderGuiOverlayEvent.Post event, CallbackInfo callback) {
        if (SporeSrpHudForeground.shouldCancelOriginal()) callback.cancel();
    }
}
