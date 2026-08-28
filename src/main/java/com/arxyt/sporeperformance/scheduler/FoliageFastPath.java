package com.arxyt.sporeperformance.scheduler;

import com.Harbinger.Spore.Sentities.FoliageSpread;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lazy implementation of Spore's foliage conversion decision tree.  The original method reads
 * six neighbours before drawing any of its probability gates.  Most positions do not enter a
 * branch that needs a neighbour, so this keeps the same branch order while loading states on
 * demand and at most once per position.
 */
public final class FoliageFastPath {
    public static void apply(FoliageSpread spread, Level level, BlockState state, BlockPos pos) {
        if (!PerformanceConfig.REFACTOR_FOLIAGE_LAZY_NEIGHBOR_READS.get()) {
            legacy(spread, level, state, pos);
            return;
        }
        Neighbours neighbours = new Neighbours(level, pos);
        if (Math.random() < 0.1D && state.isSolidRender(level, pos)
                && (neighbours.nonSolid(Direction.NORTH) || neighbours.nonSolid(Direction.SOUTH)
                || neighbours.nonSolid(Direction.WEST) || neighbours.nonSolid(Direction.EAST)
                || neighbours.nonSolid(Direction.UP) || neighbours.nonSolid(Direction.DOWN))) {
            spread.convertBlocks(state, level, pos);
        }
        if (Math.random() < 0.2D) {
            spread.convertWood(level, state, pos);
            spread.placeRottenBush(neighbours.get(Direction.UP), level, pos, state);
        }
        if (Math.random() < 0.1D) spread.convertFromJson(level, state, pos);
        if (Math.random() < 0.01D) spread.placeGroundFoliage(neighbours.get(Direction.UP), level, pos, state);
        if (Math.random() < 0.1D) spread.placeCropsFoliage(level, pos, state);
        if (Math.random() < 0.01D) spread.placeWaterFoliage(neighbours.get(Direction.UP), level, pos, state);
        if (Math.random() < 0.01D) spread.placeHangingFoliage(neighbours.get(Direction.DOWN), level, pos, state);
        if (Math.random() < 0.01D) {
            BlockState north = neighbours.get(Direction.NORTH);
            BlockState south = neighbours.get(Direction.SOUTH);
            BlockState west = neighbours.get(Direction.WEST);
            BlockState east = neighbours.get(Direction.EAST);
            spread.placeWallFoliage(north, south, west, east,
                    !north.isSolidRender(level, pos.north()), !south.isSolidRender(level, pos.south()),
                    !west.isSolidRender(level, pos.west()), !east.isSolidRender(level, pos.east()),
                    level, pos, state);
        }
    }

    private static void legacy(FoliageSpread spread, Level level, BlockState state, BlockPos pos) {
        BlockState north = level.getBlockState(pos.north());
        BlockState south = level.getBlockState(pos.south());
        BlockState west = level.getBlockState(pos.west());
        BlockState east = level.getBlockState(pos.east());
        BlockState above = level.getBlockState(pos.above());
        BlockState below = level.getBlockState(pos.below());
        boolean northT = !north.isSolidRender(level, pos.north());
        boolean southT = !south.isSolidRender(level, pos.south());
        boolean westT = !west.isSolidRender(level, pos.west());
        boolean eastT = !east.isSolidRender(level, pos.east());
        boolean aboveT = !above.isSolidRender(level, pos.above());
        boolean belowT = !below.isSolidRender(level, pos.below());
        if (Math.random() < 0.1D && state.isSolidRender(level, pos)
                && (northT || southT || westT || eastT || aboveT || belowT)) spread.convertBlocks(state, level, pos);
        if (Math.random() < 0.2D) {
            spread.convertWood(level, state, pos);
            spread.placeRottenBush(above, level, pos, state);
        }
        if (Math.random() < 0.1D) spread.convertFromJson(level, state, pos);
        if (Math.random() < 0.01D) spread.placeGroundFoliage(above, level, pos, state);
        if (Math.random() < 0.1D) spread.placeCropsFoliage(level, pos, state);
        if (Math.random() < 0.01D) spread.placeWaterFoliage(above, level, pos, state);
        if (Math.random() < 0.01D) spread.placeHangingFoliage(below, level, pos, state);
        if (Math.random() < 0.01D) spread.placeWallFoliage(north, south, west, east,
                northT, southT, westT, eastT, level, pos, state);
    }

    private static final class Neighbours {
        private final Level level;
        private final BlockPos origin;
        private final BlockState[] states = new BlockState[6];
        private int mask;

        private Neighbours(Level level, BlockPos origin) {
            this.level = level;
            this.origin = origin;
        }

        private BlockState get(Direction direction) {
            int bit = 1 << direction.ordinal();
            int index = direction.ordinal();
            if ((mask & bit) == 0) {
                states[index] = level.getBlockState(origin.relative(direction));
                mask |= bit;
            }
            return states[index];
        }

        private boolean nonSolid(Direction direction) {
            BlockPos neighbour = origin.relative(direction);
            return !get(direction).isSolidRender(level, neighbour);
        }
    }

    private FoliageFastPath() {}
}
