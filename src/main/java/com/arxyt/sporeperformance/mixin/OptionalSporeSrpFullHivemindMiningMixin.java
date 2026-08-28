package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.compat.LazySphereQueue;
import com.arxyt.sporeperformance.compat.SporeSrpBlockBudget;
import com.arxyt.sporeperformance.compat.SporeSrpBackgroundScheduler;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraftforge.event.TickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Removes the 50-radius Full Hivemind mining allocation only in aggressive mode.
 * The disabled branch is intentionally a byte-for-byte semantic equivalent of sporesrp's
 * original generator, including its distance ordering.
 */
@Pseudo
@Mixin(targets = "com.maha_fish.sporesrp.handler.FullHivemindHandler", remap = false)
abstract class OptionalSporeSrpFullHivemindMiningMixin {
    @Inject(method = "processMining", at = @At("HEAD"), require = 0)
    private void sporeperformance$beginMiningBudget(TickEvent.ServerTickEvent event, CallbackInfo callback) {
        SporeSrpBlockBudget.beginTick(event.getServer().getTickCount());
    }

    @Redirect(method = "processMining", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", remap = false), require = 0)
    private int sporeperformance$capSphereMiningSlice(int proposedEnd, int queueSize) {
        return SporeSrpBlockBudget.capEnd(proposedEnd, queueSize);
    }

    @Redirect(method = {"buildCasings", "buildCasingsOnce"}, at = @At(value = "INVOKE", target = "Lcom/maha_fish/sporesrp/util/CasingBuilder;buildCasing(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;II)V", remap = false), require = 0)
    private static void sporeperformance$scheduleCasing(Level level, BlockPos center, int radius, int thickness) {
        SporeSrpBackgroundScheduler.INSTANCE.buildCasing(level, center, radius, thickness);
    }

    /**
     * @author Spore Performance
     * @reason Replaces the aggressive-mode eager 523k+ BlockPos allocation with a cursor list.
     */
    @Overwrite(remap = false)
    public static List<BlockPos> generateSphereQueue(BlockPos center, int radius) {
        if (PerformanceConfig.AGGRESSIVE_SPORESRP_LAZY_HIVEMIND_QUEUE.get()) {
            return new LazySphereQueue(center, radius);
        }
        ArrayList<BlockPos> result = new ArrayList<>();
        int radiusSquared = radius * radius;
        for (int dx = -radius; dx <= radius; ++dx) {
            for (int dy = -radius; dy <= radius; ++dy) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    if (dx * dx + dy * dy + dz * dz <= radiusSquared) result.add(center.offset(dx, dy, dz));
                }
            }
        }
        result.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        return result;
    }
}
