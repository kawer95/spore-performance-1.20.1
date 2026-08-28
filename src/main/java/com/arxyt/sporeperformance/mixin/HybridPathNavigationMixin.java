package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.AI.HybridPathNavigation;
import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import com.arxyt.sporeperformance.ai.PathNavigationView;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = HybridPathNavigation.class, remap = false)
abstract class HybridPathNavigationMixin {
    @Inject(method = "m_6570_", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$sharedHybridPath(Entity target, int reach, CallbackInfoReturnable<Path> callback) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get() || !PerformanceConfig.REFACTOR_NAVIGATION_ENABLED.get()
                || !(sporeperformance$mob().level() instanceof ServerLevel level) || !FungalAiRuntime.isSpore(sporeperformance$mob())) return;
        Mob mob = sporeperformance$mob();
        Path cached = FungalAiRuntime.INSTANCE.get(level).paths.cachedNativePath(mob, target);
        if (cached != null) callback.setReturnValue(cached);
    }

    @Inject(method = "m_6570_", at = @At("RETURN"))
    private void sporeperformance$recordHybridPath(Entity target, int reach, CallbackInfoReturnable<Path> callback) {
        if (PerformanceConfig.REFACTOR_AI_ENABLED.get() && PerformanceConfig.REFACTOR_NAVIGATION_ENABLED.get()
                && sporeperformance$mob().level() instanceof ServerLevel level && FungalAiRuntime.isSpore(sporeperformance$mob()))
            FungalAiRuntime.INSTANCE.get(level).paths.recordNativePath(sporeperformance$mob(), target, callback.getReturnValue());
    }

    @Inject(method = "m_7864_", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$sharedHybridPositionPath(BlockPos target, int reach, CallbackInfoReturnable<Path> callback) {
        Mob mob = sporeperformance$mob();
        if (!(mob instanceof com.Harbinger.Spore.Sentities.BaseEntities.Calamity calamity)
                || !PerformanceConfig.REFACTOR_AI_ENABLED.get() || !PerformanceConfig.REFACTOR_NAVIGATION_ENABLED.get()
                || !PerformanceConfig.REFACTOR_CALAMITY_POSITION_PATH_CACHE.get()
                || !(mob.level() instanceof ServerLevel level)) return;
        var runtime = FungalAiRuntime.INSTANCE.get(level);
        if (runtime.calamities.suppressPositionRequest(calamity)) {
            callback.setReturnValue(null);
            return;
        }
        runtime.calamities.submitPositionIntent(calamity, target, 1.0D, "hybrid_position_create_path");
        Path cached = runtime.paths.cachedNativePath(mob, target);
        if (cached != null) callback.setReturnValue(cached);
    }

    @Inject(method = "m_7864_", at = @At("RETURN"))
    private void sporeperformance$recordHybridPositionPath(BlockPos target, int reach, CallbackInfoReturnable<Path> callback) {
        Mob mob = sporeperformance$mob();
        if (mob instanceof com.Harbinger.Spore.Sentities.BaseEntities.Calamity
                && PerformanceConfig.REFACTOR_AI_ENABLED.get() && PerformanceConfig.REFACTOR_NAVIGATION_ENABLED.get()
                && PerformanceConfig.REFACTOR_CALAMITY_POSITION_PATH_CACHE.get()
                && mob.level() instanceof ServerLevel level) {
            FungalAiRuntime.INSTANCE.get(level).paths.recordNativePath(mob, target, callback.getReturnValue());
        }
    }

    @Inject(method = "m_5624_", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$honestMoveResult(Entity target, double speed, CallbackInfoReturnable<Boolean> callback) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get() || !PerformanceConfig.REFACTOR_NAVIGATION_ENABLED.get()
                || !FungalAiRuntime.isSpore(sporeperformance$mob())) return;
        PathNavigation navigation = (PathNavigation) (Object) this;
        if (sporeperformance$mob() instanceof com.Harbinger.Spore.Sentities.BaseEntities.Calamity calamity
                && sporeperformance$mob().level() instanceof ServerLevel level) {
            var runtime = FungalAiRuntime.INSTANCE.get(level);
            if (runtime.calamities.suppressMoveRequest(calamity, target)) {
                callback.setReturnValue(true);
                return;
            }
            runtime.calamities.submitEntityIntent(calamity, target, speed);
            Path path = navigation.createPath(target, 0);
            if (path == null) {
                callback.setReturnValue(runtime.calamities
                        .recordEntityPathResult(calamity, target, null, speed));
                return;
            }
            runtime.calamities.recordEntityPathResult(calamity, target, path, speed);
            callback.setReturnValue(navigation.moveTo(path, speed));
            return;
        }
        Path path = navigation.createPath(target, 0);
        callback.setReturnValue(path != null && navigation.moveTo(path, speed));
    }

    @Inject(method = "m_26577_", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$suppressHybridNativeStuck(CallbackInfoReturnable<Boolean> callback) {
        Mob mob = sporeperformance$mob();
        if (mob instanceof com.Harbinger.Spore.Sentities.BaseEntities.Calamity calamity
                && mob.level() instanceof ServerLevel level
                && FungalAiRuntime.INSTANCE.get(level).calamities.suppressNativeStuckRecompute(calamity)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "m_7638_", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$suppressHybridNavigationDuringBackoff(org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        Mob mob = sporeperformance$mob();
        if (mob instanceof com.Harbinger.Spore.Sentities.BaseEntities.Calamity calamity
                && mob.level() instanceof ServerLevel level
                && FungalAiRuntime.INSTANCE.get(level).calamities.suppressNavigationTick(calamity)) {
            callback.cancel();
        }
    }

    private Mob sporeperformance$mob() {
        return ((PathNavigationView) this).sporeperformance$getMob();
    }
}
