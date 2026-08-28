package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.AI.CalamitiesAI.CalamityInfectedCommand;
import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** High-performance equivalent of AI Fix's corrected Calamity command broadcast. */
@Mixin(value = CalamityInfectedCommand.class, remap = false, priority = 900)
abstract class AiFixCalamityCommandMixin {
    @Shadow @Final private Calamity calamity;

    @Inject(method = "Targeting", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$indexedCommand(Entity source, CallbackInfo callback) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get() || !PerformanceConfig.REFACTOR_GROUP_COORDINATION.get()
                || !(source.level() instanceof ServerLevel level)) return;
        AABB area = source.getBoundingBox().inflate(32.0D);
        for (Infected infected : FungalAiRuntime.query(level, source, area, Infected.class)) {
            if (infected.getTarget() == null && calamity.getTarget() != null
                    && calamity.getTarget().isAlive() && !calamity.getTarget().isInvulnerable()) {
                infected.setTarget(calamity.getTarget());
            }
            if (infected.getSearchPos() == null && !BlockPos.ZERO.equals(calamity.getSearchArea())) {
                infected.setSearchPos(calamity.getSearchArea());
            }
        }
        callback.cancel();
    }
}
