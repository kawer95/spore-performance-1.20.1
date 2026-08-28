package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.world.ItemOptimizationPolicy;
import com.arxyt.sporeperformance.world.ManagedItemEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ItemEntity.class)
abstract class ItemEntityMixin implements ManagedItemEntity {
    @Shadow private int age;
    @Shadow private int pickupDelay;
    @Shadow private UUID thrower;
    @Shadow private UUID target;
    @Unique private boolean sporeperformance$playerDropped;
    @Unique private boolean sporeperformance$lifetimeConfigured;
    @Unique private boolean sporeperformance$expiryReported;

    @Inject(method = "mergeWithNeighbours", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$useCoordinator(CallbackInfo callback) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!self.level().isClientSide && ItemOptimizationPolicy.managedForMerge(self)) callback.cancel();
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Z"))
    private boolean sporeperformance$skipStationaryCollisionQuery(net.minecraft.world.level.Level level,
                                                                   net.minecraft.world.entity.Entity entity,
                                                                   net.minecraft.world.phys.AABB box) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (com.arxyt.sporeperformance.config.PerformanceConfig.AGGRESSIVE_STATIONARY_ITEM_PHYSICS_LOD.get()
                && !level.isClientSide && self.onGround() && !self.isInWater() && !self.isInLava()
                && self.getDeltaMovement().horizontalDistanceSqr() < 1.0E-5D
                && Math.floorMod(self.tickCount + self.getId(),
                    com.arxyt.sporeperformance.config.PerformanceConfig.AGGRESSIVE_STATIONARY_ITEM_INTERVAL.get()) != 0
                && level.getNearestPlayer(self,
                    com.arxyt.sporeperformance.config.PerformanceConfig.AGGRESSIVE_STATIONARY_ITEM_WAKE_DISTANCE.get()) == null) {
            com.arxyt.sporeperformance.diagnostics.PerformanceMetrics.increment("items.stationary_collision_query_avoided");
            return true;
        }
        return level.noCollision(entity, box);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void sporeperformance$saveFlags(CompoundTag tag, CallbackInfo callback) {
        if (sporeperformance$playerDropped) tag.putBoolean("SporePerformancePlayerDrop", true);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void sporeperformance$loadFlags(CompoundTag tag, CallbackInfo callback) {
        sporeperformance$playerDropped = tag.getBoolean("SporePerformancePlayerDrop");
        sporeperformance$lifetimeConfigured = false;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void sporeperformance$recordNaturalExpiry(CallbackInfo callback) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!sporeperformance$expiryReported && self.isRemoved() && age >= self.lifespan) {
            sporeperformance$expiryReported = true;
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(self.getItem().getItem());
            com.arxyt.sporeperformance.diagnostics.PerformanceMetrics.increment("items.expired." + key);
        }
    }

    @Override public boolean sporeperformance$isPlayerDropped() { return sporeperformance$playerDropped; }
    @Override public void sporeperformance$setPlayerDropped(boolean value) { sporeperformance$playerDropped = value; }
    @Override public boolean sporeperformance$isLifetimeConfigured() { return sporeperformance$lifetimeConfigured; }
    @Override public void sporeperformance$setLifetimeConfigured(boolean value) { sporeperformance$lifetimeConfigured = value; }
    @Override public int sporeperformance$getAge() { return age; }
    @Override public void sporeperformance$setAge(int value) { age = value; }
    @Override public int sporeperformance$getPickupDelay() { return pickupDelay; }
    @Override public void sporeperformance$setPickupDelay(int value) { pickupDelay = value; }
    @Override public UUID sporeperformance$getThrower() { return thrower; }
    @Override public UUID sporeperformance$getTarget() { return target; }
}
