package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
abstract class ProjectileMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void sporeperformance$removeOrphan(CallbackInfo callback) {
        if (!PerformanceConfig.AGGRESSIVE_ORPHAN_PROJECTILE_CLEANUP.get()) return;
        Projectile self = (Projectile) (Object) this;
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(self.getType());
        if (!self.level().isClientSide && key != null && "spore".equals(key.getNamespace())
                && self.tickCount >= PerformanceConfig.AGGRESSIVE_ORPHAN_PROJECTILE_LIFETIME.get()
                && (self.getOwner() == null || self.getOwner().isRemoved())) self.discard();
    }
}
