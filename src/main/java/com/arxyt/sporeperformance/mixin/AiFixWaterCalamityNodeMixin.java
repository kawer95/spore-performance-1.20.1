package com.arxyt.sporeperformance.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.pathfinder.SwimNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/** Standalone port used only when AI Fix is absent. */
@Mixin(targets = "com.Harbinger.Spore.Sentities.AI.CalamityPathNavigation$WaterCalamityNodeEvaluator", remap = false)
abstract class AiFixWaterCalamityNodeMixin extends SwimNodeEvaluator {
    protected AiFixWaterCalamityNodeMixin() { super(true); }

    @Overwrite
    public BlockPathTypes m_8086_(BlockGetter getter, int x, int y, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, y, z);
        if (getter.getBlockState(pos).isPathfindable(getter, pos, PathComputationType.WATER)) return BlockPathTypes.WATER;
        if (getter.getBlockState(pos).isPathfindable(getter, pos, PathComputationType.LAND)) return BlockPathTypes.OPEN;
        return super.getBlockPathType(getter, x, y, z);
    }
}
