package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import com.arxyt.sporeperformance.ai.SporeTickContext;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Mixin(Level.class)
abstract class LevelEntityQueryMixin {
    @Inject(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
            at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void sporeperformance$routeSporeLivingQuery(EntityTypeTest<Entity, T> typeTest, AABB bounds,
                                                                            Predicate<? super T> filter,
                                                                            CallbackInfoReturnable<List<T>> callback) {
        Level self = (Level) (Object) this;
        Entity source = SporeTickContext.current();
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get() || !PerformanceConfig.REFACTOR_SHARED_PERCEPTION.get()
                || !(self instanceof ServerLevel level) || source == null || source.level() != self
                || !FungalAiRuntime.isSpore(source)
                || !LivingEntity.class.isAssignableFrom(typeTest.getBaseClass())) return;
        List<? extends LivingEntity> candidates = FungalAiRuntime.query(level, source, bounds, LivingEntity.class);
        List<T> result = new ArrayList<>(candidates.size());
        for (LivingEntity candidate : candidates) {
            T cast = typeTest.tryCast(candidate);
            if (cast != null && filter.test(cast)) result.add(cast);
        }
        PerformanceMetrics.increment("ai_refactor.custom_tick.world_queries_avoided");
        PerformanceMetrics.add("ai_refactor.custom_tick.query_candidates", candidates.size());
        callback.setReturnValue(result);
    }
}
