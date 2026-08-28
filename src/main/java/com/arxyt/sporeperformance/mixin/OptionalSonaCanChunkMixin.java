package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.compat.SonaCanChunkTickCache;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps Sona's global infection enablement lookup to one evaluation per level per game tick. */
@Pseudo
@Mixin(targets = "com.scarasol.sona.manager.InfectionManager", remap = false)
public abstract class OptionalSonaCanChunkMixin {
    @Inject(method = "canChunkInfection", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sporePerformance$reuse(Level level, CallbackInfoReturnable<Boolean> callback) {
        Boolean cached = SonaCanChunkTickCache.get(level);
        if (cached != null) callback.setReturnValue(cached);
    }

    @Inject(method = "canChunkInfection", at = @At("RETURN"), remap = false)
    private static void sporePerformance$remember(Level level, CallbackInfoReturnable<Boolean> callback) {
        SonaCanChunkTickCache.put(level, callback.getReturnValue());
    }
}
