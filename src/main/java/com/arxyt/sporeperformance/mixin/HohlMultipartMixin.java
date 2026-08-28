package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.BaseEntities.HohlMultipart;
import com.Harbinger.Spore.Core.SConfig;
import com.Harbinger.Spore.ExtremelySusThings.Utilities;
import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import com.arxyt.sporeperformance.accessor.EntityBaseTickAccessor;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.damagesource.DamageTypes;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = HohlMultipart.class, remap = false)
abstract class HohlMultipartMixin {
    @Unique private long sporeperformance$parentCacheTick = Long.MIN_VALUE;
    @Unique private Entity sporeperformance$parentCache;
    @Unique private boolean sporeperformance$parentCacheValid;

    @Inject(method = "getParentSafe", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$reuseParent(CallbackInfoReturnable<Entity> callback) {
        HohlMultipart self = (HohlMultipart) (Object) this;
        long now = self.level().getGameTime();
        if (sporeperformance$parentCacheValid && sporeperformance$parentCacheTick == now) {
            PerformanceMetrics.increment("multipart.parent_lookup_avoided");
            callback.setReturnValue(sporeperformance$parentCache);
        }
    }

    @Inject(method = "getParentSafe", at = @At("RETURN"))
    private void sporeperformance$rememberParent(CallbackInfoReturnable<Entity> callback) {
        HohlMultipart self = (HohlMultipart) (Object) this;
        sporeperformance$parentCacheTick = self.level().getGameTime();
        sporeperformance$parentCache = callback.getReturnValue();
        sporeperformance$parentCacheValid = true;
    }

    @Inject(method = "setParentId", at = @At("HEAD"))
    private void sporeperformance$invalidateParent(java.util.UUID id, CallbackInfo callback) {
        sporeperformance$parentCacheValid = false;
        sporeperformance$parentCache = null;
    }

    /**
     * Multipart segments are hitboxes driven by Hohlfresser.  Running the full LivingEntity
     * physics stack here was the dominant cost in both Spark captures.  The parent already
     * owns movement and combat; this path retains only the segment-specific lifecycle.
     */
    @Inject(method = "m_8119_", at = @At("HEAD"), cancellable = true, remap = false)
    private void sporeperformance$minimalServerTick(CallbackInfo callback) {
        HohlMultipart self = (HohlMultipart) (Object) this;
        if (self.level().isClientSide || !PerformanceConfig.REFACTOR_MULTIPART_MINIMAL_TICK.get()
                || !PerformanceConfig.REFACTOR_AI_ENABLED.get()) return;

        ((EntityBaseTickAccessor) (Object) self).sporeperformance$baseTick();
        if (self.tickCount > 1) {
            Entity parent = self.getParentSafe();
            if (parent == null || parent.isRemoved() ||
                    (parent instanceof com.Harbinger.Spore.Sentities.Calamities.Hohlfresser leviathan
                            && !java.util.Objects.equals(leviathan.getChildId(), self.getUUID()))
                    || parent.distanceTo(self) > 25.0D) {
                self.remove(Entity.RemovalReason.DISCARDED);
            } else if (parent instanceof LivingEntity living) {
                self.hurtTime = living.hurtTime;
                self.deathTime = living.deathTime;
            }
        }
        if (self.tickCount % 100 == 0) self.refreshDimensions();
        if (self.tickCount % 30 == 0
                && self.getSegmentVariant() == HohlMultipart.SegmentVariants.MELEE
                && !self.isTail() && self.level() instanceof ServerLevel level) {
            if (PerformanceConfig.REFACTOR_MULTIPART_SHARED_MELEE_QUERY.get()) {
                AABB area = self.getBoundingBox().inflate(1.5D);
                float damage = (float) (SConfig.SERVER.hohl_damage.get()
                        * SConfig.SERVER.global_damage.get() / 2.0F);
                for (LivingEntity target : FungalAiRuntime.query(level, self, area, LivingEntity.class)) {
                    if (Utilities.TARGET_SELECTOR.Test(target)) {
                        target.hurt(level.damageSources().mobAttack(self), damage);
                    }
                }
                PerformanceMetrics.increment("multipart.shared_melee_query");
            } else {
                self.dealMeleeDamageAround();
            }
        }
        PerformanceMetrics.increment("multipart.minimal_tick");
        callback.cancel();
    }
}
