package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.world.TransientBlockEntityRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies transient-block admission before vanilla removes the block at the fall call site. */
@Mixin(FallingBlockEntity.class)
abstract class FallingBlockEntityMixin {
    @Inject(method = "fall", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sporeperformance$limitNewFallingBlock(Level level, BlockPos position, BlockState state,
                                                               CallbackInfoReturnable<FallingBlockEntity> callback) {
        if (!TransientBlockEntityRuntime.INSTANCE.reserveFalling(level, position, state)) {
            callback.setReturnValue(null);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, require = 0)
    private void sporeperformance$expireOldFallingBlock(CallbackInfo callback) {
        FallingBlockEntity self = (FallingBlockEntity) (Object) this;
        if (TransientBlockEntityRuntime.INSTANCE.shouldExpireFalling(self)) {
            self.discard();
            callback.cancel();
        }
    }
}
