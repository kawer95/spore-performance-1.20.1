package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.Harbinger.Spore.Sentities.MovementControls.CalamityMovementControl;
import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Intercepts the actual 90-degree write in Spore's base calamity move controller.  The runtime
 * only rejects it after proving that the current path is circular; ordinary turning is untouched.
 */
@Mixin(value = CalamityMovementControl.class, remap = false)
abstract class CalamityMovementControlMixin {
    @Redirect(method = "m_8126_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;m_146922_(F)V", remap = false))
    private void sporeperformance$rejectCircularMovementYaw(Mob mob, float requestedYaw) {
        if (mob instanceof Calamity calamity && mob.level() instanceof ServerLevel level) {
            mob.setYRot(FungalAiRuntime.INSTANCE.get(level).calamities.navigationYaw(calamity, requestedYaw));
            return;
        }
        mob.setYRot(requestedYaw);
    }
}
