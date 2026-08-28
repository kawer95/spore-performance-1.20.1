package com.arxyt.sporeperformance.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Bridge implemented on Stahlmorder without depending on SporeAI Fix classes. */
public interface StahlAiControl {
    void sporeperformance$beginControlledLeap(Vec3 landingTarget);

    int sporeperformance$decideAnimation(LivingEntity target);

    void sporeperformance$applyAttackEffect(LivingEntity target, int state);
}
