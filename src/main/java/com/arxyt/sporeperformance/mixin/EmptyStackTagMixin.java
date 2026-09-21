package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.compat.CorruptEntityNbtGuard;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Enforces Minecraft's empty-stack sentinel as immutable, detached metadata. */
@Mixin(ItemStack.class)
public abstract class EmptyStackTagMixin {
    @Inject(method = "getOrCreateTag", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$detachedEmptyTag(CallbackInfoReturnable<CompoundTag> callback) {
        if (CorruptEntityNbtGuard.emptyStackProtectionEnabled()
                && ((ItemStack) (Object) this).isEmpty()) callback.setReturnValue(new CompoundTag());
    }

    @Inject(method = "setTag", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$rejectEmptyTag(CompoundTag tag, CallbackInfo callback) {
        if (CorruptEntityNbtGuard.emptyStackProtectionEnabled()
                && ((ItemStack) (Object) this).isEmpty() && tag != null && !tag.isEmpty()) callback.cancel();
    }
}
