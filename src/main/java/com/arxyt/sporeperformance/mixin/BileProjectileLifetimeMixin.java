package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.Projectile.BileProjectile;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.DebugTrace;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spore's BileProjectile uses Entity.tickCount for its 300-tick expiry.  tickCount is not
 * serialized, so a projectile saved in an unloaded chunk starts a fresh 300-tick lifetime.
 */
@Mixin(value = BileProjectile.class, remap = false)
abstract class BileProjectileLifetimeMixin {
    @Unique private static final String SPOREPERFORMANCE_BILE_LIFETIME = "spore_performance:BileTotalLifetime";
    @Unique private int sporeperformance$bileLifetime;
    @Unique private boolean sporeperformance$legacyBileLifetime;
    @Unique private boolean sporeperformance$bileLifetimeInitialized;

    @Inject(method = "m_8119_", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$expireByTotalLifetime(CallbackInfo callback) {
        BileProjectile self = (BileProjectile) (Object) this;
        if (self.level().isClientSide || !PerformanceConfig.SAFE_PERSIST_BILE_PROJECTILE_LIFETIME.get()) return;

        CompoundTag persistentData = self.getPersistentData();
        if (!sporeperformance$bileLifetimeInitialized) {
            sporeperformance$bileLifetimeInitialized = true;
            boolean stored = persistentData.contains(SPOREPERFORMANCE_BILE_LIFETIME);
            sporeperformance$bileLifetime = stored ? persistentData.getInt(SPOREPERFORMANCE_BILE_LIFETIME) : 0;
            sporeperformance$legacyBileLifetime = !stored;
        }

        if (sporeperformance$legacyBileLifetime) {
            sporeperformance$legacyBileLifetime = false;
            PerformanceMetrics.increment("projectiles.bile.legacy_loaded");
            if (self.level() instanceof ServerLevel level && DebugTrace.enabled(DebugTrace.Category.BACKGROUND)) {
                DebugTrace.event(DebugTrace.Category.BACKGROUND, level, DebugTrace.trace(self), self,
                        "bile_legacy_lifetime_initialized", "storedLifetimeMissing=true");
            }
        }

        ++sporeperformance$bileLifetime;
        persistentData.putInt(SPOREPERFORMANCE_BILE_LIFETIME, sporeperformance$bileLifetime);
        if (sporeperformance$bileLifetime < PerformanceConfig.SAFE_BILE_PROJECTILE_LIFETIME_TICKS.get()) return;
        self.discard();
        PerformanceMetrics.increment("projectiles.bile.expired_total_lifetime");
        if (self.level() instanceof ServerLevel level && DebugTrace.enabled(DebugTrace.Category.BACKGROUND)) {
            DebugTrace.event(DebugTrace.Category.BACKGROUND, level, DebugTrace.trace(self), self,
                    "bile_expired_total_lifetime", "ticks=" + sporeperformance$bileLifetime);
        }
        callback.cancel();
    }

}
