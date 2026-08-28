package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.world.RemoteIdleAiController;
import com.arxyt.sporeperformance.ai.StaticEntityPolicy;
import com.arxyt.sporeperformance.ai.BusserVariantGoalRuntime;
import com.Harbinger.Spore.Sentities.EvolvedInfected.Busser;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Leaves physics, navigation, effects and syncing untouched; only selector evaluation is throttled. */
@Mixin(Mob.class)
abstract class MobRemoteIdleAiMixin {
    /**
     * A work-token suspension happens before vanilla enters senses, goals,
     * navigation and controls.  Entity physics has already run in aiStep(),
     * so this deliberately does not freeze gravity, collisions, effects,
     * burning, damage handling or network state.
     */
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$suspendUnscheduledServerAi(CallbackInfo callback) {
        if (StaticEntityPolicy.suspendServerAi((Mob) (Object) this)) {
            StaticEntityPolicy.maintainMinimalServerAi((Mob) (Object) this);
            com.arxyt.sporeperformance.diagnostics.PerformanceMetrics.increment("ai.static_entity_server_ai_suspended");
            callback.cancel();
            return;
        }
        if ((Mob) (Object) this instanceof Busser) BusserVariantGoalRuntime.enter((Busser) (Object) this);
        if (RemoteIdleAiController.suspendServerAi((Mob) (Object) this)) {
            if ((Mob) (Object) this instanceof Busser) BusserVariantGoalRuntime.leave();
            callback.cancel();
        }
    }

    @Inject(method = "serverAiStep", at = @At("RETURN"), require = 0)
    private void sporeperformance$leaveBusserVariantAi(CallbackInfo callback) {
        if ((Mob) (Object) this instanceof Busser) BusserVariantGoalRuntime.leave();
    }

    @Redirect(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V"))
    private void sporeperformance$throttleSelectorTick(GoalSelector selector) {
        if (!RemoteIdleAiController.skipSelectors((Mob) (Object) this)) selector.tick();
    }

    @Redirect(method = "serverAiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tickRunningGoals(Z)V"))
    private void sporeperformance$throttleRunningGoals(GoalSelector selector, boolean tickAll) {
        if (!RemoteIdleAiController.skipSelectors((Mob) (Object) this)) selector.tickRunningGoals(tickAll);
    }

    /** Mound/GastGeber remain fully physical, but rooted idle units do not need control ticks. */
    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;tick()V"), require = 0)
    private void sporeperformance$skipStaticNavigation(PathNavigation navigation) {
        if (!StaticEntityPolicy.suspendServerAi((Mob) (Object) this)) navigation.tick();
        else com.arxyt.sporeperformance.diagnostics.PerformanceMetrics.increment("ai.static_entity_navigation_skipped");
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/control/MoveControl;tick()V"), require = 0)
    private void sporeperformance$skipStaticMove(MoveControl control) {
        if (!StaticEntityPolicy.suspendServerAi((Mob) (Object) this)) control.tick();
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/control/LookControl;tick()V"), require = 0)
    private void sporeperformance$skipStaticLook(LookControl control) {
        if (!StaticEntityPolicy.suspendServerAi((Mob) (Object) this)) control.tick();
    }

    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/control/JumpControl;tick()V"), require = 0)
    private void sporeperformance$skipStaticJump(JumpControl control) {
        if (!StaticEntityPolicy.suspendServerAi((Mob) (Object) this)) control.tick();
    }
}
