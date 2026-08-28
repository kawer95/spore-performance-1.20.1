package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Core.SConfig;
import com.Harbinger.Spore.Sentities.FoliageSpread;
import com.arxyt.sporeperformance.world.InfectionConversionCache;
import com.arxyt.sporeperformance.scheduler.FoliageFastPath;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/** Replaces only the repeated string parsing in the interface's default block-conversion method. */
@Mixin(value = FoliageSpread.class, remap = false)
interface FoliageSpreadMixin {
    @Overwrite
    default void SpreadFoliageAndConvert(Level level, BlockState blockstate, BlockPos blockpos) {
        FoliageFastPath.apply((FoliageSpread) this, level, blockstate, blockpos);
    }

    /**
     * @author ARXYT
     * @reason Keep the original fallback path while moving normal mapping parsing to config reload.
     */
    @Overwrite
    default void convertBlocks(BlockState state, Level level, BlockPos pos) {
        if (InfectionConversionCache.convert(level, state, pos)) return;
        for (String rule : SConfig.DATAGEN.block_infection.get()) {
            String[] pair = rule.split("\\|");
            if (pair.length != 2) continue;
            Block from = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(pair[0]));
            Block to = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(pair[1]));
            if (from == state.getBlock() && to != null) level.setBlock(pos, to.defaultBlockState(), 3);
        }
    }
}
