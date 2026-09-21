package com.arxyt.sporeperformance.mixin;

import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Removes per-entity Proto/Mound join INFO spam while preserving registration and biome work. */
@Mixin(targets = "com.maha_fish.sporesrp.handler.InfectedBiomeHandler", remap = false)
public abstract class OptionalSporeSrpJoinLogMixin {
    @Redirect(method = "onEntityJoin", at = @At(value = "INVOKE",
            target = "Lorg/apache/logging/log4j/Logger;info(Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V"),
            remap = false)
    private void sporeperformance$suppressJoinInfo(Logger logger, String message, Object first, Object second) {
        // Intentionally silent: this method can run thousands of times in one battle.
    }
}
