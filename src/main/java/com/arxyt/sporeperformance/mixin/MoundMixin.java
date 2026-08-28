package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.Organoids.Mound;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.scheduler.FungalWorkScheduler;
import com.arxyt.sporeperformance.world.FungalWorkBudget;
import com.arxyt.sporeperformance.diagnostics.DebugTrace;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bridges Mound's private state and diverts only the aggressive, synchronous tendril scan. */
@Mixin(value = Mound.class, remap = false)
abstract class MoundMixin {
    @Redirect(method = "m_8119_", at = @At(value = "INVOKE", target = "Lcom/Harbinger/Spore/Sentities/Organoids/Mound;SpreadInfection(Lnet/minecraft/world/level/Level;DLnet/minecraft/core/BlockPos;)V", remap = false))
    private void sporeperformance$scheduleFoliage(Mound mound, Level level, double range, net.minecraft.core.BlockPos origin) {
        if (!level.isClientSide && !FungalWorkBudget.INSTANCE.mayWork(mound, FungalWorkBudget.WorkKind.MOUND)) return;
        if (!level.isClientSide && FungalWorkScheduler.INSTANCE.queueFoliage(mound, range, origin)) {
            if (DebugTrace.enabled(DebugTrace.Category.BACKGROUND) && level instanceof ServerLevel serverLevel)
                DebugTrace.event(DebugTrace.Category.BACKGROUND, serverLevel, DebugTrace.trace(mound), mound,
                        "mound_foliage_queued", "range=" + range + ",origin=" + origin);
            return;
        }
        mound.SpreadInfection(level, range, origin);
    }

    @Inject(method = "SpreadKin", at = @At("HEAD"), cancellable = true, require = 0)
    private void sporeperformance$queueTendril(Entity entity, Level level, CallbackInfo callback) {
        if (!level.isClientSide && !FungalWorkBudget.INSTANCE.mayWork((Mound) (Object) this, FungalWorkBudget.WorkKind.MOUND)) {
            callback.cancel();
            return;
        }
        if (!level.isClientSide && FungalWorkScheduler.INSTANCE.queueTendril((Mound) (Object) this)) {
            Mound mound = (Mound) (Object) this;
            if (DebugTrace.enabled(DebugTrace.Category.BACKGROUND) && level instanceof ServerLevel serverLevel)
                DebugTrace.event(DebugTrace.Category.BACKGROUND, serverLevel, DebugTrace.trace(mound), mound,
                        "mound_tendril_queued", "source=" + (entity == null ? "" : entity.getUUID()));
            callback.cancel();
        }
    }
}
