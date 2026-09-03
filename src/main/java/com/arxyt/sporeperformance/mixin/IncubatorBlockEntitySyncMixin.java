package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.SBlockEntities.IncubatorBlockEntity;
import com.arxyt.sporeperformance.compat.BlockEntitySync;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Synchronizes the rendered item and active state without sending fuel updates every tick. */
@Mixin(value = IncubatorBlockEntity.class, remap = false)
abstract class IncubatorBlockEntitySyncMixin {
    @Shadow public int fuel;
    @Unique private boolean sporeperformance$fuelWasActive;

    @Inject(method = "setFuel", at = @At("HEAD"))
    private void sporeperformance$captureFuelState(int value, CallbackInfo callback) {
        sporeperformance$fuelWasActive = fuel > 0;
    }

    @Inject(method = "setFuel", at = @At("RETURN"))
    private void sporeperformance$syncFuelTransition(int value, CallbackInfo callback) {
        if (sporeperformance$fuelWasActive != (fuel > 0)) sporeperformance$sync();
    }

    @Inject(method = "m_6836_", at = @At("RETURN"))
    private void sporeperformance$syncInsertedItem(int slot, ItemStack stack, CallbackInfo callback) {
        sporeperformance$sync();
    }

    @Inject(method = "m_7407_", at = @At("RETURN"))
    private void sporeperformance$syncRemovedItem(int slot, int count,
                                                  CallbackInfoReturnable<ItemStack> callback) {
        if (!callback.getReturnValue().isEmpty()) sporeperformance$sync();
    }

    @Inject(method = "m_6211_", at = @At("RETURN"))
    private void sporeperformance$syncClearedItems(CallbackInfo callback) {
        sporeperformance$sync();
    }

    @Unique
    private void sporeperformance$sync() {
        BlockEntitySync.send((BlockEntity) (Object) this);
    }
}
