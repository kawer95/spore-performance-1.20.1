package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Effect.Marker;
import com.arxyt.sporeperformance.compat.CorruptEntityNbtGuard;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Marker has no item cure; returning ItemStack.EMPTY serialized the globally shared empty tag. */
@Mixin(Marker.class)
public abstract class MarkerCurativeItemsMixin {
    @Inject(method = "getCurativeItems", at = @At("HEAD"), cancellable = true, remap = false)
    private void sporeperformance$emptyCuratives(CallbackInfoReturnable<List<ItemStack>> callback) {
        if (CorruptEntityNbtGuard.markerSanitizerEnabled()) callback.setReturnValue(List.of());
    }
}
