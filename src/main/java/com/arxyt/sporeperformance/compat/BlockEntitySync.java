package com.arxyt.sporeperformance.compat;

import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Sends one authoritative block-entity snapshot after a render-relevant state transition. */
public final class BlockEntitySync {
    public static void send(BlockEntity entity) {
        Level level = entity.getLevel();
        if (level == null || level.isClientSide) return;
        entity.setChanged();
        BlockState state = entity.getBlockState();
        level.sendBlockUpdated(entity.getBlockPos(), state, state, Block.UPDATE_CLIENTS);
    }

    private BlockEntitySync() {}
}
