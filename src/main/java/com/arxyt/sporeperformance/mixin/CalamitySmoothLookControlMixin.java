package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.Harbinger.Spore.Sentities.MovementControls.SmoothLookControl;
import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * SmoothLookControl is intended to smooth head tracking, but Spore also writes its result into
 * YRot and YBodyRot.  That competes with CalamityMovementControl every tick and produces the
 * repeated large turns seen while a path is blocked.  We retain its head/pitch calculation and
 * restore only the movement-owned body axes afterwards.
 */
@Mixin(value = SmoothLookControl.class, remap = false)
abstract class CalamitySmoothLookControlMixin {
    @Shadow @Final private Mob mob;
    @Unique private boolean sporeperformance$restoreBodyYaw;
    @Unique private float sporeperformance$yaw;
    @Unique private float sporeperformance$bodyYaw;
    @Unique private float sporeperformance$yawOld;
    @Unique private float sporeperformance$bodyYawOld;

    @Inject(method = "m_8128_", at = @At("HEAD"))
    private void sporeperformance$captureMovementYaw(CallbackInfo callback) {
        sporeperformance$restoreBodyYaw = mob instanceof Calamity calamity
                && mob.level() instanceof ServerLevel level
                && FungalAiRuntime.INSTANCE.get(level).calamities.ownBodyYaw(calamity);
        if (!sporeperformance$restoreBodyYaw) return;
        sporeperformance$yaw = mob.getYRot();
        sporeperformance$bodyYaw = mob.yBodyRot;
        sporeperformance$yawOld = mob.yRotO;
        sporeperformance$bodyYawOld = mob.yBodyRotO;
    }

    @Inject(method = "m_8128_", at = @At("TAIL"))
    private void sporeperformance$restoreMovementYaw(CallbackInfo callback) {
        if (!sporeperformance$restoreBodyYaw) return;
        mob.setYRot(sporeperformance$yaw);
        mob.setYBodyRot(sporeperformance$bodyYaw);
        mob.yRotO = sporeperformance$yawOld;
        mob.yBodyRotO = sporeperformance$bodyYawOld;
        sporeperformance$restoreBodyYaw = false;
    }
}
