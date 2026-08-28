package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraftforge.event.TickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AI Fix retains its immediate damage/death/removal hooks.  This only spaces its defensive
 * registry sweep, which otherwise walks every protected permanent entity at both tick phases.
 */
@Pseudo
@Mixin(targets = "com.exhuashan.sporeaifix.util.ImmortalEntityRegistry", remap = false)
abstract class OptionalImmortalAuditMixin {
    @Inject(method = "auditProtectedEntities", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sporeperformance$spaceNonCriticalAudit(TickEvent.ServerTickEvent event, CallbackInfo callback) {
        if (!PerformanceConfig.AGGRESSIVE_SPOREFIX_PERMANENT_AUDIT.get()) return;
        int interval = PerformanceConfig.AGGRESSIVE_SPOREFIX_PERMANENT_AUDIT_INTERVAL.get();
        if (interval > 1 && Math.floorMod(event.getServer().getTickCount(), interval) != 0) callback.cancel();
    }
}
