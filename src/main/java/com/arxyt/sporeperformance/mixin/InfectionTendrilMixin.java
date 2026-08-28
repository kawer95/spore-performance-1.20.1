package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Core.SConfig;
import com.Harbinger.Spore.Core.Sblocks;
import com.Harbinger.Spore.Core.Sentities;
import com.Harbinger.Spore.SBlockEntities.ContainerBlockEntity;
import com.Harbinger.Spore.SBlockEntities.LivingStructureBlocks;
import com.Harbinger.Spore.Sentities.Organoids.Mound;
import com.Harbinger.Spore.Sentities.Utility.InfectionTendril;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.world.InfectionConversionCache;
import com.arxyt.sporeperformance.world.FungalWorkBudget;
import com.arxyt.sporeperformance.diagnostics.DebugTrace;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

/**
 * Keeps InfectionTendril's native 10-tick spread cadence and chances, while avoiding six neighbour
 * block reads for the 98% of positions that fail the conversion roll and eliminating repeated
 * infection-rule parsing, registry lookups and ItemStack allocation on the remaining positions.
 */
@Mixin(value = InfectionTendril.class, remap = false)
abstract class InfectionTendrilMixin {
    /**
     * @author ARXYT
     * @reason The original spread reparses every configured infection rule for every local position.
     */
    @Overwrite
    private void Spread(Entity entity, Level level, double value) {
        if (!PerformanceConfig.SAFE_TENDRIL_SPREAD_FAST_PATH.get()) {
            sporeperformance$legacySpread(entity, level, value);
            return;
        }

        InfectionTendril tendril = (InfectionTendril) (Object) this;
        if (!level.isClientSide && !FungalWorkBudget.INSTANCE.mayWork(tendril, FungalWorkBudget.WorkKind.TENDRIL)) return;
        AABB aabb = entity.getBoundingBox().inflate(value);
        if (DebugTrace.enabled(DebugTrace.Category.BACKGROUND) && level instanceof ServerLevel serverLevel)
            DebugTrace.event(DebugTrace.Category.BACKGROUND, serverLevel, DebugTrace.trace(tendril), tendril,
                    "tendril_spread_started", "range=" + value + ",volume="
                            + ((Mth.floor(aabb.maxX) - Mth.floor(aabb.minX) + 1L)
                            * (Mth.floor(aabb.maxY) - Mth.floor(aabb.minY) + 1L)
                            * (Mth.floor(aabb.maxZ) - Mth.floor(aabb.minZ) + 1L)));
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(aabb.minX), Mth.floor(aabb.minY), Mth.floor(aabb.minZ),
                Mth.floor(aabb.maxX), Mth.floor(aabb.maxY), Mth.floor(aabb.maxZ))) {
            sporeperformance$spreadAt(tendril, level, pos);
        }
    }

    @Unique
    private void sporeperformance$spreadAt(InfectionTendril tendril, Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockPos abovePos = pos.above();
        BlockState above = level.getBlockState(abovePos);
        boolean solid = state.isSolidRender(level, pos);
        // Keep the native random-call cadence: the original rolls once for every position before
        // testing solidity/exposure, even though only a solid exposed position can convert.
        double conversionRoll = Math.random();

        if (conversionRoll < 0.02D && solid && sporeperformance$hasOpenNeighbour(level, pos, above)) {
            sporeperformance$convert(level, state, pos);
        }
        if (above.isAir() && solid && Math.random() < 0.1D) {
            level.setBlock(abovePos, Sblocks.MYCELIUM_VEINS.get().defaultBlockState(), 3);
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (state.is(Sblocks.REMAINS.get())) {
            Mound mound = new Mound((EntityType<? extends PathfinderMob>) (EntityType<?>) Sentities.MOUND.get(), level);
            mound.setMaxAge(tendril.getAgeM());
            mound.tickEmerging();
            mound.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            level.addFreshEntity(mound);
            level.removeBlock(pos, false);
            tendril.discard();
            if (DebugTrace.enabled(DebugTrace.Category.BACKGROUND) && level instanceof ServerLevel serverLevel)
                DebugTrace.event(DebugTrace.Category.BACKGROUND, serverLevel, DebugTrace.trace(tendril), tendril,
                        "tendril_became_mound", "reason=remains,pos=" + pos);
            return;
        }
        if (blockEntity instanceof Container container && sporeperformance$hasFood(container)) {
            sporeperformance$eatFood(container);
            Mound mound = new Mound((EntityType<? extends PathfinderMob>) (EntityType<?>) Sentities.MOUND.get(), level);
            mound.setMaxAge(1);
            mound.tickEmerging();
            mound.setPos(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
            level.addFreshEntity(mound);
            level.removeBlock(abovePos, false);
            tendril.discard();
            if (DebugTrace.enabled(DebugTrace.Category.BACKGROUND) && level instanceof ServerLevel serverLevel)
                DebugTrace.event(DebugTrace.Category.BACKGROUND, serverLevel, DebugTrace.trace(tendril), tendril,
                        "tendril_became_mound", "reason=food_container,pos=" + pos);
            return;
        }
        if (state.is(Sblocks.HIVE_SPAWN.get()) || state.is(Sblocks.BIOMASS_LUMP.get())) {
            if (blockEntity instanceof LivingStructureBlocks structureBlocks) {
                structureBlocks.setKills(structureBlocks.getKills() + SConfig.SERVER.mound_tendril_feed.get());
                tendril.discard();
            }
            return;
        }
        if (state.is(Blocks.SPAWNER)) {
            level.setBlock(pos, Sblocks.OVERGROWN_SPAWNER.get().defaultBlockState(), 2);
            tendril.discard();
        }
    }

    @Unique
    private boolean sporeperformance$hasOpenNeighbour(Level level, BlockPos pos, BlockState above) {
        return !level.getBlockState(pos.north()).isSolidRender(level, pos.north())
                || !level.getBlockState(pos.south()).isSolidRender(level, pos.south())
                || !level.getBlockState(pos.west()).isSolidRender(level, pos.west())
                || !level.getBlockState(pos.east()).isSolidRender(level, pos.east())
                || !above.isSolidRender(level, pos.above())
                || !level.getBlockState(pos.below()).isSolidRender(level, pos.below());
    }

    @Unique
    private void sporeperformance$convert(Level level, BlockState state, BlockPos pos) {
        if (InfectionConversionCache.convert(level, state, pos)) return;
        for (String rule : SConfig.DATAGEN.block_infection.get()) {
            String[] pair = rule.split("\\|");
            if (pair.length != 2) continue;
            Block from = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(pair[0]));
            ItemStack source = new ItemStack(from);
            if (source == ItemStack.EMPTY || state.getBlock().asItem() != source.getItem()) continue;
            Block to = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(pair[1]));
            ItemStack target = new ItemStack(to);
            if (target != ItemStack.EMPTY && target.getItem() instanceof BlockItem blockItem) {
                level.setBlock(pos, blockItem.getBlock().defaultBlockState(), 3);
            }
        }
    }

    @Unique
    private boolean sporeperformance$hasFood(Container container) {
        return !(container instanceof ContainerBlockEntity) && container.hasAnyMatching(ItemStack::isEdible);
    }

    @Unique
    private void sporeperformance$eatFood(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); ++slot) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEdible()) stack.setCount(0);
        }
    }

    /** Exact native fallback for administrators who disable the safe fast path. */
    @Unique
    private void sporeperformance$legacySpread(Entity entity, Level level, double value) {
        InfectionTendril tendril = (InfectionTendril) (Object) this;
        AABB aabb = entity.getBoundingBox().inflate(value);
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(aabb.minX), Mth.floor(aabb.minY), Mth.floor(aabb.minZ),
                Mth.floor(aabb.maxX), Mth.floor(aabb.maxY), Mth.floor(aabb.maxZ))) {
            BlockState north = level.getBlockState(pos.north());
            BlockState south = level.getBlockState(pos.south());
            BlockState west = level.getBlockState(pos.west());
            BlockState east = level.getBlockState(pos.east());
            BlockState above = level.getBlockState(pos.above());
            BlockState below = level.getBlockState(pos.below());
            BlockState state = level.getBlockState(pos);
            boolean exposed = !north.isSolidRender(level, pos.north()) || !south.isSolidRender(level, pos.south())
                    || !west.isSolidRender(level, pos.west()) || !east.isSolidRender(level, pos.east())
                    || !above.isSolidRender(level, pos.above()) || !below.isSolidRender(level, pos.below());
            if (Math.random() < 0.02D && state.isSolidRender(level, pos) && exposed) {
                for (String rule : SConfig.DATAGEN.block_infection.get()) {
                    String[] pair = rule.split("\\|");
                    if (pair.length != 2) continue;
                    ItemStack source = new ItemStack(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(pair[0])));
                    if (source == ItemStack.EMPTY || state.getBlock().asItem() != source.getItem()) continue;
                    ItemStack target = new ItemStack(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(pair[1])));
                    if (target != ItemStack.EMPTY && target.getItem() instanceof BlockItem blockItem) {
                        level.setBlock(pos, blockItem.getBlock().defaultBlockState(), 3);
                    }
                }
            }
            if (above.isAir() && state.isSolidRender(level, pos) && Math.random() < 0.1D) {
                level.setBlock(pos.above(), Sblocks.MYCELIUM_VEINS.get().defaultBlockState(), 3);
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (state.is(Sblocks.REMAINS.get())) {
                Mound mound = new Mound((EntityType<? extends PathfinderMob>) (EntityType<?>) Sentities.MOUND.get(), level);
                mound.setMaxAge(tendril.getAgeM());
                mound.tickEmerging();
                mound.setPos(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                level.addFreshEntity(mound);
                level.removeBlock(pos, false);
                tendril.discard();
            } else if (blockEntity instanceof Container container && sporeperformance$hasFood(container)) {
                sporeperformance$eatFood(container);
                Mound mound = new Mound((EntityType<? extends PathfinderMob>) (EntityType<?>) Sentities.MOUND.get(), level);
                mound.setMaxAge(1);
                mound.tickEmerging();
                mound.setPos(pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D);
                level.addFreshEntity(mound);
                level.removeBlock(pos.above(), false);
                tendril.discard();
            } else if (state.is(Sblocks.HIVE_SPAWN.get()) || state.is(Sblocks.BIOMASS_LUMP.get())) {
                if (blockEntity instanceof LivingStructureBlocks structureBlocks) {
                    structureBlocks.setKills(structureBlocks.getKills() + SConfig.SERVER.mound_tendril_feed.get());
                    tendril.discard();
                }
            } else if (state.is(Blocks.SPAWNER)) {
                level.setBlock(pos, Sblocks.OVERGROWN_SPAWNER.get().defaultBlockState(), 2);
                tendril.discard();
            }
        }
    }
}
