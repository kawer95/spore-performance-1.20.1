package com.arxyt.sporeperformance.ai;

import com.Harbinger.Spore.Sentities.AI.BusserFlyAndDrop;
import com.Harbinger.Spore.Sentities.AI.BusserSwellGoal;
import com.Harbinger.Spore.Sentities.AI.PhayerGrabAndDropTargets;
import com.Harbinger.Spore.Sentities.AI.PullGoal;
import com.Harbinger.Spore.Sentities.AI.TransportInfected;
import com.Harbinger.Spore.Sentities.AI.CalamitiesAI.ScatterShotRangedGoal;
import com.Harbinger.Spore.Sentities.EvolvedInfected.Busser;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.world.entity.ai.goal.Goal;

/** Thread-local owner for pruning Busser's variant-inapplicable canUse calls. */
public final class BusserVariantGoalRuntime {
    private static final ThreadLocal<Busser> CURRENT = new ThreadLocal<>();
    private static volatile boolean active;

    public static void enter(Busser busser) {
        if (PerformanceConfig.REFACTOR_BUSSER_ENABLED.get()
                && PerformanceConfig.REFACTOR_BUSSER_VARIANT_GOAL_PRUNING.get()) {
            CURRENT.set(busser);
            active = true;
        } else {
            CURRENT.remove();
            active = false;
        }
    }

    public static void leave() { CURRENT.remove(); active = false; }

    public static boolean skip(Goal goal) {
        if (!active) return false;
        Busser busser = CURRENT.get();
        if (busser == null || !PerformanceConfig.REFACTOR_BUSSER_VARIANT_GOAL_PRUNING.get()) return false;
        int variant = busser.getTypeVariant();
        boolean irrelevant = switch (variant) {
            case 1 -> goal instanceof BusserFlyAndDrop || goal instanceof PullGoal
                    || goal instanceof TransportInfected || goal instanceof BusserSwellGoal
                    || goal instanceof ScatterShotRangedGoal;
            case 2 -> goal instanceof BusserFlyAndDrop || goal instanceof PullGoal
                    || goal instanceof TransportInfected || goal instanceof PhayerGrabAndDropTargets
                    || goal instanceof ScatterShotRangedGoal;
            case 3 -> goal instanceof BusserFlyAndDrop || goal instanceof PullGoal
                    || goal instanceof TransportInfected || goal instanceof BusserSwellGoal
                    || goal instanceof PhayerGrabAndDropTargets
                    || goal.getClass().getName().endsWith("Busser$1");
            default -> goal instanceof BusserSwellGoal || goal instanceof PhayerGrabAndDropTargets
                    || goal instanceof ScatterShotRangedGoal;
        };
        if (irrelevant) PerformanceMetrics.increment("busser.variant_goal_pruned");
        return irrelevant;
    }

    private BusserVariantGoalRuntime() {}
}
