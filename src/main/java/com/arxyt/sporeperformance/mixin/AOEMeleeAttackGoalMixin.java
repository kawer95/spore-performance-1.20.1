package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.AI.AOEMeleeAttackGoal;
import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.DebugTrace;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;
import java.util.function.Predicate;

@Mixin(value = AOEMeleeAttackGoal.class, remap = false)
abstract class AOEMeleeAttackGoalMixin {
    @Shadow @Final protected PathfinderMob mob;

    @Redirect(method = "checkAndPerformAttack", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_6443_(Ljava/lang/Class;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;", remap = false), require = 0)
    private <T extends LivingEntity> List<T> sporeperformance$sharedAoeCandidates(Level level, Class<T> type,
                                                                                 AABB bounds, Predicate<? super T> filter) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get() || !PerformanceConfig.REFACTOR_SHARED_PERCEPTION.get()
                || !(level instanceof ServerLevel serverLevel)) return level.getEntitiesOfClass(type, bounds, filter);
        List<T> result = FungalAiRuntime.query(serverLevel, mob, bounds, type).stream().filter(filter).toList();
        if (DebugTrace.enabled(DebugTrace.Category.COMBAT))
            DebugTrace.event(DebugTrace.Category.COMBAT, serverLevel, DebugTrace.trace(mob), mob,
                    "aoe_candidates", "type=" + type.getName() + ",count=" + result.size());
        return result;
    }
}
