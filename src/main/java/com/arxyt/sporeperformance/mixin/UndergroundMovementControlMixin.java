package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.Harbinger.Spore.Sentities.MovementControls.UndergroundMovementControl;
import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Applies the same invalid-route guard to Hohlfresser's direct underground controller. */
@Mixin(value = UndergroundMovementControl.class, remap = false)
abstract class UndergroundMovementControlMixin {
    @Redirect(method = "moveUnderground", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Mob;m_146922_(F)V", remap = false))
    private void sporeperformance$rejectCircularUndergroundYaw(Mob mob, float requestedYaw) {
        if (mob instanceof Calamity calamity && mob.level() instanceof ServerLevel level) {
            mob.setYRot(FungalAiRuntime.INSTANCE.get(level).calamities.navigationYaw(calamity, requestedYaw));
            return;
        }
        mob.setYRot(requestedYaw);
    }
}
