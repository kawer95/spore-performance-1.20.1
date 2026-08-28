package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.Calamities.Grakensenker;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps vortex physics at 10 Hz; movement, targeting and attacks remain every tick. */
@Mixin(value = Grakensenker.class, remap = false, priority = 900)
abstract class AiFixGrakensenkerWorkMixin {
    @Inject(method = "applyVortexForces", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$halveVortexScans(CallbackInfo callback) {
        Grakensenker self = (Grakensenker) (Object) this;
        if (PerformanceConfig.REFACTOR_AI_ENABLED.get() && (self.tickCount & 1) != 0) callback.cancel();
    }
}
