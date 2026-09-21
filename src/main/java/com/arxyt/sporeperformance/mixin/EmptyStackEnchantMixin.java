package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.compat.CorruptEntityNbtGuard;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Empty stacks are sentinels, not enchantable inventory objects. */
@Mixin(ItemStack.class)
public abstract class EmptyStackEnchantMixin {
    @Inject(method = "enchant", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$rejectEmptyEnchant(Enchantment enchantment, int level, CallbackInfo callback) {
        if (CorruptEntityNbtGuard.emptyStackProtectionEnabled()
                && ((ItemStack) (Object) this).isEmpty()) callback.cancel();
    }
}
