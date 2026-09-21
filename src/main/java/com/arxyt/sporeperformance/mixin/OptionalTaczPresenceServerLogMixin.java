package com.arxyt.sporeperformance.mixin;

import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Suppression packets remain functional; only their per-bullet server INFO record is removed. */
@Mixin(targets = "com.tacz.presence.server.BulletImpactHandler", remap = false)
public abstract class OptionalTaczPresenceServerLogMixin {
    @Redirect(method = "checkSuppressionForTrajectory", at = @At(value = "INVOKE",
            target = "Lorg/slf4j/Logger;info(Ljava/lang/String;[Ljava/lang/Object;)V"), remap = false)
    private static void sporeperformance$suppressBulletInfo(Logger logger, String message, Object[] arguments) {
        // Intentionally silent.
    }
}
