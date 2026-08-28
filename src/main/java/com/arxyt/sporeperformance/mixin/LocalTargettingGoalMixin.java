package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.AI.LocHiv.LocalTargettingGoal;
import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.arxyt.sporeperformance.world.GroupSensingCache;
import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Predicate;

/** Shares short-lived neighbour lists only for the aggressive linked-target broadcast path. */
@Mixin(value = LocalTargettingGoal.class, remap = false)
abstract class LocalTargettingGoalMixin {
    @Shadow @Final private Infected mob;

    @Inject(method = "Targeting", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$eventDrivenPropagation(net.minecraft.world.entity.Entity source, CallbackInfo callback) {
        if (PerformanceConfig.REFACTOR_AI_ENABLED.get() && PerformanceConfig.REFACTOR_GROUP_COORDINATION.get()
                && mob.level() instanceof ServerLevel level) {
            FungalAiRuntime.INSTANCE.get(level).groups.propagateLinked(mob);
            callback.cancel();
        }
    }

    @Redirect(method = "Targeting", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", remap = false))
    private <T extends LivingEntity> List<T> sporeperformance$shareNeighbourQuery(Level level, Class<T> type, AABB bounds, Predicate<? super T> filter) {
        return GroupSensingCache.query(mob, level, type, bounds, filter);
    }
}
