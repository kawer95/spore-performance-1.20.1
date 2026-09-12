package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.AI.CalamitiesAI.ScatterShotRangedGoal;
import com.Harbinger.Spore.Sentities.Calamities.Howitzer;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Removes the temporary List and unloaded-chunk access from Howitzer's pre-volley fire scan. */
@Mixin(value = Howitzer.HowitzerRangedAttackGoal.class, remap = false)
abstract class HowitzerRangedGoalScanMixin extends ScatterShotRangedGoal {
    protected HowitzerRangedGoalScanMixin(RangedAttackMob mob, double speed, int interval,
                                          float range, int min, int max) {
        super(mob, speed, interval, range, min, max);
    }

    @Inject(method = "getBurnable", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$countBurnableWithoutList(LivingEntity target,
                                                            CallbackInfoReturnable<Integer> callback) {
        if (!PerformanceConfig.REFACTOR_HOWITZER_BURNABLE_FAST_SCAN.get()
                || !(mob.level() instanceof ServerLevel level)) return;
        AABB box = target.getBoundingBox().inflate(4.0D);
        int minX = (int) Math.floor(box.minX);
        int minY = (int) Math.floor(box.minY);
        int minZ = (int) Math.floor(box.minZ);
        int maxX = (int) Math.floor(box.maxX);
        int maxY = (int) Math.floor(box.maxY);
        int maxZ = (int) Math.floor(box.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int count = 0;
        int visited = 0;
        int cachedChunkX = Integer.MIN_VALUE;
        int cachedChunkZ = Integer.MIN_VALUE;
        LevelChunk cachedChunk = null;
        for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    visited++;
                    pos.set(x, y, z);
                    int chunkX = x >> 4;
                    int chunkZ = z >> 4;
                    if (chunkX != cachedChunkX || chunkZ != cachedChunkZ) {
                        cachedChunkX = chunkX;
                        cachedChunkZ = chunkZ;
                        cachedChunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                    }
                    if (cachedChunk == null) continue;
                    BlockState state = cachedChunk.getBlockState(pos);
                    if (state.isFlammable(level, pos, Direction.UP)) count++;
                }
            }
        }
        PerformanceMetrics.add("ai_refactor.howitzer.burnable_scan_positions", visited);
        callback.setReturnValue(count);
    }
}
