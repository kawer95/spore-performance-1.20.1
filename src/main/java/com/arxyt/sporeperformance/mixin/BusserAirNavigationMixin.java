package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.ai.AirSweepContext;
import com.arxyt.sporeperformance.ai.PathNavigationView;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bounds the expensive shortcut sweep used by Busser's experimental air navigator. */
@Mixin(targets = "com.Harbinger.Spore.Sentities.AI.NeuralProcessing.Experimental.ExpAirPathNavigation", remap = false)
abstract class BusserAirNavigationMixin {
    @Shadow abstract boolean sweep(Vec3 vector, Vec3 base, Vec3 max);
    @Unique private Path sporeperformance$lastSweepPath;
    @Unique private Vec3 sporeperformance$lastSweepPosition;
    @Unique private long sporeperformance$lastSweepTick = Long.MIN_VALUE;
    @Unique private long sporeperformance$lastSweepTerrain = Long.MIN_VALUE;
    @Unique private int sporeperformance$lastSweepNode = -1;
    @Unique private int sporeperformance$lastShortcutNode = -1;
    @Unique private boolean sporeperformance$lastSweepResult;

    @Inject(method = "tryShortcut", at = @At("HEAD"), cancellable = true, require = 0)
    private void sporeperformance$boundShortcutSweep(Path path, Vec3 entityPos, int pathLength,
                                                      Vec3 base, Vec3 max,
                                                      CallbackInfoReturnable<Boolean> callback) {
        if (!PerformanceConfig.REFACTOR_BUSSER_ENABLED.get()
                || !PerformanceConfig.REFACTOR_BUSSER_SHARED_AIR_SWEEP_CONTEXT.get()) return;
        net.minecraft.world.entity.Mob mob = ((PathNavigationView) (Object) this).sporeperformance$getMob();
        long now = mob.level().getGameTime();
        long terrain = AirSweepContext.terrainVersion(mob.level());
        int currentNode = path.getNextNodeIndex();
        int refresh = Math.max(1, PerformanceConfig.REFACTOR_BUSSER_SHORTCUT_REFRESH_TICKS.get());
        if (sporeperformance$lastSweepPath == path && sporeperformance$lastSweepPosition != null
                && now - sporeperformance$lastSweepTick < refresh
                && sporeperformance$lastSweepNode == currentNode
                && sporeperformance$lastSweepTerrain == terrain
                && sporeperformance$lastSweepPosition.distanceToSqr(entityPos) <= 0.25D) {
            if (!sporeperformance$lastSweepResult && sporeperformance$lastShortcutNode >= 0)
                path.setNextNodeIndex(sporeperformance$lastShortcutNode);
            PerformanceMetrics.increment("busser.air_shortcut_result_reused");
            callback.setReturnValue(sporeperformance$lastSweepResult);
            return;
        }
        int budget = Math.max(1, PerformanceConfig.REFACTOR_BUSSER_SHORTCUT_CANDIDATES_PER_TICK.get());
        int tested = 0;
        for (int i = pathLength; --i > path.getNextNodeIndex() && tested < budget;) {
            tested++;
            Vec3 vector = path.getEntityPosAtNode(mob, i).subtract(entityPos);
            if (this.sweep(vector, base, max)) {
                path.setNextNodeIndex(i);
                rememberSweep(path, entityPos, now, terrain, currentNode, i, false);
                PerformanceMetrics.increment("busser.air_shortcut_hit");
                callback.setReturnValue(false);
                return;
            }
        }
        PerformanceMetrics.add("busser.air_shortcut_candidates", tested);
        rememberSweep(path, entityPos, now, terrain, currentNode, -1, true);
        callback.setReturnValue(true);
    }

    @Unique
    private void rememberSweep(Path path, Vec3 position, long tick, long terrain, int node, int shortcut, boolean result) {
        sporeperformance$lastSweepPath = path;
        sporeperformance$lastSweepPosition = position;
        sporeperformance$lastSweepTick = tick;
        sporeperformance$lastSweepTerrain = terrain;
        sporeperformance$lastSweepNode = node;
        sporeperformance$lastShortcutNode = shortcut;
        sporeperformance$lastSweepResult = result;
    }

    @Redirect(method = "sweep", at = @At(value = "NEW",
            target = "net.minecraft.world.level.PathNavigationRegion"), require = 0)
    private PathNavigationRegion sporeperformance$reuseSweepRegion(Level level, BlockPos min, BlockPos max) {
        return AirSweepContext.region(level, min, max);
    }
}
