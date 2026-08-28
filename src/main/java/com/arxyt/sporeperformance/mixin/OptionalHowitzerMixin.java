package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.Calamities.Howitzer;
import com.arxyt.sporeperformance.compat.HowitzerLosCache;
import com.arxyt.sporeperformance.compat.HowitzerTrajectoryBudget;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applied only with the AI Fix present; caches its overwritten visibility calculation. */
@Mixin(value = Howitzer.class, remap = false)
abstract class OptionalHowitzerMixin {
    @Unique private boolean sporeperformance$selectingReplacement;

    // The installed production jar keeps the SRG name; ForgeGradle's
    // official-mapped dev class exposes the same vanilla override as
    // hasLineOfSight.  Listing both keeps the optional cache testable in
    // development without weakening the production fail-closed gate.
    @Inject(method = {"m_142582_", "hasLineOfSight"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void sporeperformance$reuseTrajectory(Entity target, CallbackInfoReturnable<Boolean> callback) {
        if (!PerformanceConfig.SAFE_HOWITZER_SAME_TICK_CACHE.get()) return;
        Boolean cached = HowitzerLosCache.find((Entity) (Object) this, target);
        if (cached != null) callback.setReturnValue(cached);
    }

    @Inject(method = {"m_142582_", "hasLineOfSight"}, at = @At("RETURN"), require = 0)
    private void sporeperformance$cacheTrajectory(Entity target, CallbackInfoReturnable<Boolean> callback) {
        if (PerformanceConfig.SAFE_HOWITZER_SAME_TICK_CACHE.get()) {
            HowitzerLosCache.put((Entity) (Object) this, target, callback.getReturnValue());
        }
    }

    @Inject(method = "sporeAiFix$findNearestTarget", at = @At("HEAD"), require = 0)
    private void sporeperformance$beginReplacementScan(Howitzer self, net.minecraft.world.entity.LivingEntity currentTarget, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.entity.LivingEntity> callback) {
        sporeperformance$selectingReplacement = true;
    }

    @Inject(method = "sporeAiFix$findNearestTarget", at = @At("RETURN"), require = 0)
    private void sporeperformance$endReplacementScan(Howitzer self, net.minecraft.world.entity.LivingEntity currentTarget, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.entity.LivingEntity> callback) {
        sporeperformance$selectingReplacement = false;
    }

    @Inject(method = "sporeAiFix$selectTrajectory", at = @At("HEAD"), cancellable = true, require = 0)
    private void sporeperformance$budgetNewTrajectory(Howitzer self, net.minecraft.world.entity.LivingEntity target, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> callback) {
        if (sporeperformance$selectingReplacement && !HowitzerTrajectoryBudget.allow((Entity) (Object) this)) callback.setReturnValue(0);
    }
}
