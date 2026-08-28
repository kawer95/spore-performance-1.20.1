package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.AI.CalamityPathNavigation;
import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import com.arxyt.sporeperformance.runtime.CalamityPathBackoff;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.server.level.ServerLevel;
import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.WeakHashMap;

@Mixin(value = CalamityPathNavigation.class, remap = false)
abstract class CalamityPathNavigationMixin {
    private static final Map<PathNavigation, Long> LAST_RECOMPUTE = new WeakHashMap<>();
    private static final Map<PathNavigation, CalamityPathBackoff> BACKOFFS = new WeakHashMap<>();

    @Shadow @Final protected Calamity calamity;

    @Inject(method = "m_6570_", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$sharedCalamityPath(Entity target, int reach, CallbackInfoReturnable<Path> callback) {
        if (!com.arxyt.sporeperformance.ai.CalamityNavigationRuntime.enabled(calamity)
                || !(calamity.level() instanceof ServerLevel level)) return;
        Path cached = FungalAiRuntime.INSTANCE.get(level).paths.cachedNativePath(calamity, target);
        if (cached != null) callback.setReturnValue(cached);
    }

    @Inject(method = "m_6570_", at = @At("RETURN"))
    private void sporeperformance$recordCalamityPath(Entity target, int reach, CallbackInfoReturnable<Path> callback) {
        if (com.arxyt.sporeperformance.ai.CalamityNavigationRuntime.enabled(calamity)
                && calamity.level() instanceof ServerLevel level)
            FungalAiRuntime.INSTANCE.get(level).paths.recordNativePath(calamity, target, callback.getReturnValue());
    }

    @Inject(method = "m_7864_", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$sharedCalamityPositionPath(BlockPos target, int reach, CallbackInfoReturnable<Path> callback) {
        if (!com.arxyt.sporeperformance.ai.CalamityNavigationRuntime.enabled(calamity)
                || !PerformanceConfig.REFACTOR_CALAMITY_POSITION_PATH_CACHE.get()
                || !(calamity.level() instanceof ServerLevel level)) return;
        var runtime = FungalAiRuntime.INSTANCE.get(level);
        if (runtime.calamities.suppressPositionRequest(calamity)) {
            callback.setReturnValue(null);
            return;
        }
        runtime.calamities.submitPositionIntent(calamity, target, 1.0D, "position_create_path");
        Path cached = runtime.paths.cachedNativePath(calamity, target);
        if (cached != null) callback.setReturnValue(cached);
    }

    @Inject(method = "m_7864_", at = @At("RETURN"))
    private void sporeperformance$recordCalamityPositionPath(BlockPos target, int reach, CallbackInfoReturnable<Path> callback) {
        if (com.arxyt.sporeperformance.ai.CalamityNavigationRuntime.enabled(calamity)
                && PerformanceConfig.REFACTOR_CALAMITY_POSITION_PATH_CACHE.get()
                && calamity.level() instanceof ServerLevel level) {
            FungalAiRuntime.INSTANCE.get(level).paths.recordNativePath(calamity, target, callback.getReturnValue());
        }
    }

    @Inject(method = "m_5624_", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$honestMoveResult(Entity target, double speed, CallbackInfoReturnable<Boolean> callback) {
        if (!com.arxyt.sporeperformance.ai.CalamityNavigationRuntime.enabled(calamity)) return;
        PathNavigation navigation = (PathNavigation) (Object) this;
        if (calamity.level() instanceof ServerLevel level) {
            var runtime = FungalAiRuntime.INSTANCE.get(level);
            if (runtime.calamities.suppressMoveRequest(calamity, target)) {
                // Keep the active Goal from interpreting a failed path as a new movement
                // command while recovery is backing off.  No path or yaw is re-armed.
                callback.setReturnValue(true);
                return;
            }
            runtime.calamities.submitEntityIntent(calamity, target, speed);
            Path path = navigation.createPath(target, 0);
            if (path == null) {
                // Preserve Spore's direct-approach result.  createPath has already stored the
                // private fallback position used by CalamityPathNavigation.tick().
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

    /**
     * Original Spore recomputes first and then asks vanilla whether it is stuck.  The per-level
     * runtime owns that state transition now, so this must be cancelled rather than merely
     * returning from the injector and falling through to the unconditional recompute.
     */
    @Inject(method = "m_26577_", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$backoffUnreachablePath(CallbackInfoReturnable<Boolean> callback) {
        if (PerformanceConfig.REFACTOR_AI_ENABLED.get() && PerformanceConfig.REFACTOR_NAVIGATION_ENABLED.get()
                && calamity.level() instanceof ServerLevel level
                && FungalAiRuntime.INSTANCE.get(level).calamities.suppressNativeStuckRecompute(calamity)) {
            callback.setReturnValue(false);
            return;
        }
        if (!PerformanceConfig.AGGRESSIVE_PATH_BACKOFF.get()) return;
        long now = calamity.level().getGameTime();
        synchronized (BACKOFFS) {
            CalamityPathBackoff backoff = BACKOFFS.computeIfAbsent((PathNavigation) (Object) this, ignored -> new CalamityPathBackoff());
            var target = calamity.getTarget();
            if (target != null && (backoff.targetPosition == null || backoff.targetPosition.distanceToSqr(target.position()) > 4.0D)) {
                backoff.nextAttempt = 0;
                backoff.failures = 0;
                backoff.targetPosition = target.position();
            }
            if (calamity.hurtTime > 0) { backoff.nextAttempt = 0; backoff.failures = 0; }
            if (now < backoff.nextAttempt) {
                PerformanceMetrics.increment("path.backoff_suppressed");
                callback.setReturnValue(false);
            }
        }
    }

    /** During controlled retry backoff do not let CalamityPathNavigation's direct fallback re-arm MOVE_TO. */
    @Inject(method = "m_7638_", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$suppressNavigationDuringBackoff(CallbackInfo callback) {
        if (calamity.level() instanceof ServerLevel level
                && FungalAiRuntime.INSTANCE.get(level).calamities.suppressNavigationTick(calamity)) {
            callback.cancel();
        }
    }

    /**
     * Skip path nodes already enclosed by this calamity before Spore's navigation re-arms its
     * movement controller.  This prevents the near-node orbit at its source instead of masking
     * the visible rotation afterwards.
     */
    @Inject(method = "m_7638_", at = @At("HEAD"))
    private void sporeperformance$advanceNodesAlreadyInsideCalamity(CallbackInfo callback) {
        if (calamity.level() instanceof ServerLevel level) {
            var runtime = FungalAiRuntime.INSTANCE.get(level);
            if (runtime.calamities.suppressNavigationTick(calamity)) return;
            runtime.calamities.advanceArrivedPathNodes(calamity,
                    (PathNavigation) (Object) this);
        }
    }

    @Inject(method = "m_26577_", at = @At("RETURN"))
    private void sporeperformance$recordPathResult(CallbackInfoReturnable<Boolean> callback) {
        if (PerformanceConfig.REFACTOR_AI_ENABLED.get() && PerformanceConfig.REFACTOR_NAVIGATION_ENABLED.get()) return;
        if (!PerformanceConfig.AGGRESSIVE_PATH_BACKOFF.get()) return;
        long now = calamity.level().getGameTime();
        synchronized (BACKOFFS) {
            CalamityPathBackoff backoff = BACKOFFS.computeIfAbsent((PathNavigation) (Object) this, ignored -> new CalamityPathBackoff());
            if (callback.getReturnValue()) {
                backoff.failures = Math.min(backoff.failures + 1, 3);
                backoff.nextAttempt = now + (20L << (backoff.failures - 1));
            } else {
                backoff.failures = 0;
                backoff.nextAttempt = now + PerformanceConfig.AGGRESSIVE_PATH_MIN_INTERVAL.get();
            }
        }
    }

    @Redirect(method = "m_26577_", at = @At(value = "INVOKE", target = "Lcom/Harbinger/Spore/Sentities/AI/CalamityPathNavigation;m_26569_()V", remap = false))
    private void sporeperformance$gateSameTickRecompute(CalamityPathNavigation navigation) {
        // The dragon is intentionally an untouched control specimen for this refactor.
        if (com.arxyt.sporeperformance.ai.CalamityNavigationRuntime.excluded(calamity)) {
            navigation.recomputePath();
            return;
        }
        long now = calamity.level().getGameTime();
        synchronized (LAST_RECOMPUTE) {
            Long previous = LAST_RECOMPUTE.put(navigation, now);
            if (!PerformanceConfig.SAFE_SAME_TICK_PATH_GATE.get() || previous == null || previous != now) {
                navigation.recomputePath();
                PerformanceMetrics.increment("path.recompute");
            } else {
                PerformanceMetrics.increment("path.same_tick_suppressed");
            }
        }
    }
}
