package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.SBlockEntities.OvergrownSpawnerEntity;
import com.arxyt.sporeperformance.world.SpawnerFeedOptimizer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = OvergrownSpawnerEntity.class, remap = false)
abstract class OvergrownSpawnerEntityMixin extends BlockEntity {
    protected OvergrownSpawnerEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Supplies the initial timer state to chunk data and block-entity update packets. */
    @Override
    public CompoundTag getUpdateTag() {
        return saveWithFullMetadata();
    }

    @Inject(method = "feed", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$replaceBlockScan(Level level, BlockPos position, CallbackInfo callback) {
        if (SpawnerFeedOptimizer.feed(level, position)) callback.cancel();
    }
}
