package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.arxyt.sporeperformance.runtime.GeneralPathBackoff;
import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.DebugTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PathNavigation.class)
abstract class PathNavigationMixin {
    @Shadow @Final protected Mob mob;
    @Shadow protected Path path;
    @Unique private Entity sporeperformance$pathTarget;
    @Unique private boolean sporeperformance$suppressed;

    @Inject(method = "createPath(Lnet/minecraft/world/entity/Entity;I)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$gateEntityPath(Entity target, int reach, CallbackInfoReturnable<Path> callback) {
        if (mob instanceof Calamity) return;
        sporeperformance$pathTarget = target;
        if (PerformanceConfig.REFACTOR_AI_ENABLED.get() && PerformanceConfig.REFACTOR_NAVIGATION_ENABLED.get()
                && mob.level() instanceof ServerLevel level && FungalAiRuntime.isSpore(mob)) {
            var paths = FungalAiRuntime.INSTANCE.get(level).paths;
            Path cached = paths.cachedNativePath(mob, target);
            if (cached != null) { callback.setReturnValue(cached); return; }
            BlockPos waypoint = paths.corridorWaypoint(mob, target);
            if (waypoint != null) {
                Path local = ((PathNavigation) (Object) this).createPath(waypoint, reach);
                if (local != null) {
                    if (DebugTrace.enabled(DebugTrace.Category.NAVIGATION))
                        DebugTrace.event(DebugTrace.Category.NAVIGATION, level, DebugTrace.trace(mob), mob,
                                "local_path_to_corridor", "target=" + target.getUUID() + ",waypoint=" + waypoint
                                        + ",nodes=" + local.getNodeCount());
                    callback.setReturnValue(local); return;
                }
            }
        }
        if (GeneralPathBackoff.shouldSuppress(mob, target)) {
            sporeperformance$suppressed = true;
            if (DebugTrace.enabled(DebugTrace.Category.NAVIGATION) && mob.level() instanceof ServerLevel level)
                DebugTrace.event(DebugTrace.Category.NAVIGATION, level, DebugTrace.trace(mob), mob,
                        "native_path_suppressed", "target=" + target.getUUID() + ",hasExistingPath=" + (path != null));
            callback.setReturnValue(path);
        }
    }

    @Inject(method = "createPath(Lnet/minecraft/world/entity/Entity;I)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("RETURN"))
    private void sporeperformance$recordEntityPath(Entity target, int reach, CallbackInfoReturnable<Path> callback) {
        if (PerformanceConfig.REFACTOR_AI_ENABLED.get() && PerformanceConfig.REFACTOR_NAVIGATION_ENABLED.get()
                && mob.level() instanceof ServerLevel level && FungalAiRuntime.isSpore(mob)) {
            FungalAiRuntime.INSTANCE.get(level).paths.recordNativePath(mob, target, callback.getReturnValue());
            if (DebugTrace.enabled(DebugTrace.Category.NAVIGATION))
                DebugTrace.event(DebugTrace.Category.NAVIGATION, level, DebugTrace.trace(mob), mob,
                        "native_path_result", "target=" + target.getUUID() + ",success=" + (callback.getReturnValue() != null)
                                + ",nodes=" + (callback.getReturnValue() == null ? 0 : callback.getReturnValue().getNodeCount()));
            sporeperformance$suppressed = false;
            sporeperformance$pathTarget = null;
            return;
        }
        if (mob instanceof Calamity || sporeperformance$suppressed) { sporeperformance$suppressed = false; return; }
        GeneralPathBackoff.record(mob, target, callback.getReturnValue());
        sporeperformance$pathTarget = null;
    }

    /**
     * Hybrid, underground and wall calamities inherit this coordinate overload rather than
     * CalamityPathNavigation's own implementation.  Give them the same fixed-SearchArea cache
     * without touching their specialised movement physics.  The standard Calamity navigation is
     * handled by its dedicated mixin to avoid duplicate injections.
     */
    @Inject(method = "createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$cacheSpecialCalamityPosition(BlockPos target, int reach, CallbackInfoReturnable<Path> callback) {
        if (!(mob instanceof Calamity calamity) || sporeperformance$standardCalamityNavigation()) return;
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get() || !PerformanceConfig.REFACTOR_NAVIGATION_ENABLED.get()
                || !PerformanceConfig.REFACTOR_CALAMITY_POSITION_PATH_CACHE.get()
                || !(mob.level() instanceof ServerLevel level)) return;
        FungalAiRuntime.INSTANCE.get(level).calamities.submitPositionIntent(calamity, target, 1.0D, "special_position_create_path");
        Path cached = FungalAiRuntime.INSTANCE.get(level).paths.cachedNativePath(mob, target);
        if (cached != null) callback.setReturnValue(cached);
    }

    @Inject(method = "createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;", at = @At("RETURN"))
    private void sporeperformance$recordSpecialCalamityPosition(BlockPos target, int reach, CallbackInfoReturnable<Path> callback) {
        if (!(mob instanceof Calamity) || sporeperformance$standardCalamityNavigation()) return;
        if (PerformanceConfig.REFACTOR_AI_ENABLED.get() && PerformanceConfig.REFACTOR_NAVIGATION_ENABLED.get()
                && PerformanceConfig.REFACTOR_CALAMITY_POSITION_PATH_CACHE.get()
                && mob.level() instanceof ServerLevel level) {
            FungalAiRuntime.INSTANCE.get(level).paths.recordNativePath(mob, target, callback.getReturnValue());
        }
    }

    @Unique
    private boolean sporeperformance$standardCalamityNavigation() {
        return mob.getNavigation().getClass().getName().equals("com.Harbinger.Spore.Sentities.AI.CalamityPathNavigation");
    }
}
