package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.Calamities.Howitzer;
import com.arxyt.sporeperformance.scheduler.HowitzerOreSearchScheduler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes Howitzer's monolithic ore search into the bounded server scheduler. */
@Mixin(value = Howitzer.class, remap = false)
abstract class HowitzerServerOptimizationMixin {
    @Inject(method = "searchBlocks", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$deferOreSearch(CallbackInfo callback) {
        Howitzer self = (Howitzer) (Object) this;
        // SearchArea and navigation are server-owned; the original method needlessly scans the
        // same ~38k blocks on the client every 200 ticks even though that result cannot drive AI.
        if (self.level().isClientSide || HowitzerOreSearchScheduler.INSTANCE.queue(self)) callback.cancel();
    }
}
