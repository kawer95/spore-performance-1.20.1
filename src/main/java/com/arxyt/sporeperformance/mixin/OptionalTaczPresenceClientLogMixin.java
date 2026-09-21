package com.arxyt.sporeperformance.mixin;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Removes the matching per-packet client INFO record without changing visual suppression. */
@Mixin(targets = "com.tacz.presence.client.overlay.SuppressionHandler", remap = false)
public abstract class OptionalTaczPresenceClientLogMixin {
    @Redirect(method = "onSuppressionPacket", at = @At(value = "INVOKE",
            target = "Lorg/slf4j/Logger;info(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"), remap = false)
    private static void sporeperformance$suppressPacketInfo(Logger logger, String message, Object first, Object second) {
        // Intentionally silent.
    }
}
