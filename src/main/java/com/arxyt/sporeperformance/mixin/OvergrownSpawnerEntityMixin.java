package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.SBlockEntities.OvergrownSpawnerEntity;
import com.arxyt.sporeperformance.world.SpawnerFeedOptimizer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OvergrownSpawnerEntity.class, remap = false)
abstract class OvergrownSpawnerEntityMixin {
    @Inject(method = "feed", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$replaceBlockScan(Level level, BlockPos position, CallbackInfo callback) {
        if (SpawnerFeedOptimizer.feed(level, position)) callback.cancel();
    }
}
