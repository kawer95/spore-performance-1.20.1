package com.arxyt.sporeperformance.ai;

import com.Harbinger.Spore.Sentities.Organoids.Mound;
import com.Harbinger.Spore.Sentities.Utility.GastGeber;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.world.entity.Mob;

/**
 * Centralized predicate for immobile Spore entities.  Keeping this decision outside mixins
 * makes config reloads cheap and prevents the Mound/GastGeber mixins from drifting apart.
 */
public final class StaticEntityPolicy {
    public static boolean suspendServerAi(Mob mob) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get()
                || !PerformanceConfig.REFACTOR_ENFORCE_WORK_TOKENS_BEFORE_AI.get()
                || mob.level().isClientSide || mob.isPassenger()) return false;
        if (mob instanceof Mound) {
            return PerformanceConfig.REFACTOR_MOUND_MINIMAL_TICK.get()
                    && mob.getTarget() == null && mob.hurtTime <= 0;
        }
        if (mob instanceof GastGeber geber) {
            return PerformanceConfig.REFACTOR_ROOTED_GASTGEBER_MINIMAL_TICK.get()
                    && geber.isRooted() && geber.getAggression() <= 0
                    && mob.getTarget() == null && mob.hurtTime <= 0;
        }
        return false;
    }

    /**
     * Mob.serverAiStep also owns the vanilla LivingEntity regeneration hook.
     * A static Mound must retain that one semantic side effect even though its
     * selectors/navigation are skipped.  GastGeber performs its rooted heal in
     * its own tick and therefore needs no duplicate here.
     */
    public static void maintainMinimalServerAi(Mob mob) {
        if (!(mob instanceof Mound mound) || !suspendServerAi(mob)
                || !mound.isAlive() || mound.getTicksFrozen() > 0
                || mound.tickCount % 20 != 0) return;
        if (mound.getHealth() < mound.getMaxHealth()) {
            mound.setHealth(mound.getHealth() + 1.0F);
        }
    }

    private StaticEntityPolicy() {}
}
