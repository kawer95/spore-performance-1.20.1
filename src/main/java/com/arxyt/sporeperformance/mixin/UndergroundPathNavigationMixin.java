package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.Harbinger.Spore.Sentities.MovementControls.UndergroundPathNavigation;
import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import com.arxyt.sporeperformance.ai.PathNavigationView;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Integrates Hohlfresser's specialised tunnel navigation without replacing underground physics. */
@Mixin(value = UndergroundPathNavigation.class, remap = false)
abstract class UndergroundPathNavigationMixin {
    @Inject(method = "m_6570_", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$sharedUndergroundEntityPath(Entity target, int reach, CallbackInfoReturnable<Path> callback) {
        Mob mob = sporeperformance$mob();
        if (!enabled(mob) || !(mob.level() instanceof ServerLevel level)) return;
        Path cached = FungalAiRuntime.INSTANCE.get(level).paths.cachedNativePath(mob, target);
        if (cached != null) callback.setReturnValue(cached);
    }

    @Inject(method = "m_6570_", at = @At("RETURN"))
    private void sporeperformance$recordUndergroundEntityPath(Entity target, int reach, CallbackInfoReturnable<Path> callback) {
        Mob mob = sporeperformance$mob();
        if (enabled(mob) && mob.level() instanceof ServerLevel level)
            FungalAiRuntime.INSTANCE.get(level).paths.recordNativePath(mob, target, callback.getReturnValue());
    }

    @Inject(method = "m_7864_", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$sharedUndergroundPositionPath(BlockPos target, int reach, CallbackInfoReturnable<Path> callback) {
        Mob mob = sporeperformance$mob();
        if (!enabled(mob) || !PerformanceConfig.REFACTOR_CALAMITY_POSITION_PATH_CACHE.get()
                || !(mob.level() instanceof ServerLevel level)) return;
        Calamity calamity = (Calamity) mob;
        var runtime = FungalAiRuntime.INSTANCE.get(level);
        if (runtime.calamities.suppressPositionRequest(calamity)) {
            callback.setReturnValue(null);
            return;
        }
        runtime.calamities.submitPositionIntent(calamity, target, 1.0D, "underground_position_create_path");
        Path cached = runtime.paths.cachedNativePath(mob, target);
        if (cached != null) callback.setReturnValue(cached);
    }

    @Inject(method = "m_7864_", at = @At("RETURN"))
    private void sporeperformance$recordUndergroundPositionPath(BlockPos target, int reach, CallbackInfoReturnable<Path> callback) {
        Mob mob = sporeperformance$mob();
        if (enabled(mob) && PerformanceConfig.REFACTOR_CALAMITY_POSITION_PATH_CACHE.get()
                && mob.level() instanceof ServerLevel level)
            FungalAiRuntime.INSTANCE.get(level).paths.recordNativePath(mob, target, callback.getReturnValue());
    }

    @Inject(method = "m_5624_", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$honestUndergroundMoveResult(Entity target, double speed, CallbackInfoReturnable<Boolean> callback) {
        Mob mob = sporeperformance$mob();
        if (!enabled(mob)) return;
        if (mob.level() instanceof ServerLevel level) {
            var runtime = FungalAiRuntime.INSTANCE.get(level);
            if (runtime.calamities.suppressMoveRequest((Calamity) mob, target)) {
                callback.setReturnValue(true);
                return;
            }
            runtime.calamities.submitEntityIntent((Calamity) mob, target, speed);
        }
        PathNavigation navigation = (PathNavigation) (Object) this;
        Path path = navigation.createPath(target, 0);
        Calamity calamity = (Calamity) mob;
        if (mob.level() instanceof ServerLevel level) {
            if (path == null) {
                callback.setReturnValue(FungalAiRuntime.INSTANCE.get(level).calamities
                        .recordEntityPathResult(calamity, target, null, speed));
            } else {
                FungalAiRuntime.INSTANCE.get(level).calamities.recordEntityPathResult(calamity, target, path, speed);
                callback.setReturnValue(navigation.moveTo(path, speed));
            }
            return;
        }
        callback.setReturnValue(path != null && navigation.moveTo(path, speed));
    }

    @Inject(method = "m_7638_", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$suppressUndergroundNavigationDuringBackoff(CallbackInfo callback) {
        Mob mob = sporeperformance$mob();
        if (mob instanceof Calamity calamity && mob.level() instanceof ServerLevel level
                && FungalAiRuntime.INSTANCE.get(level).calamities.suppressNavigationTick(calamity)) callback.cancel();
    }

    private static boolean enabled(Mob mob) {
        return mob instanceof Calamity calamity && CalamityNavigationRuntimeEnabled(calamity);
    }

    private static boolean CalamityNavigationRuntimeEnabled(Calamity calamity) {
        return FungalAiRuntime.isSpore(calamity)
                && com.arxyt.sporeperformance.ai.CalamityNavigationRuntime.enabled(calamity);
    }

    private Mob sporeperformance$mob() {
        return ((PathNavigationView) this).sporeperformance$getMob();
    }
}
