package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import com.arxyt.sporeperformance.ai.SporeTickContext;
import com.arxyt.sporeperformance.diagnostics.EntityTickFrame;
import com.arxyt.sporeperformance.diagnostics.DebugTrace;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;

@Mixin(ServerLevel.class)
abstract class ServerLevelEntityTickMetricsMixin {
    @Unique private static final ThreadLocal<ArrayDeque<EntityTickFrame>> SPOREPERFORMANCE$FRAMES =
            ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "tickNonPassenger", at = @At("HEAD"))
    private void sporeperformance$beginEntityTick(Entity entity, CallbackInfo callback) {
        SporeTickContext.enter(entity);
        boolean measure = PerformanceConfig.REFACTOR_TICK_PIPELINE.get()
                && (PerformanceMetrics.aiEnabled() || DebugTrace.enabled(DebugTrace.Category.GOAL))
                && FungalAiRuntime.isSpore(entity);
        SPOREPERFORMANCE$FRAMES.get().push(new EntityTickFrame(measure, measure ? System.nanoTime() : 0L, entity));
    }

    @Inject(method = "tickNonPassenger", at = @At("RETURN"))
    private void sporeperformance$endEntityTick(Entity entity, CallbackInfo callback) {
        ArrayDeque<EntityTickFrame> frames = SPOREPERFORMANCE$FRAMES.get();
        SporeTickContext.exit();
        if (frames.isEmpty()) return;
        EntityTickFrame frame = frames.pop();
        if (frames.isEmpty()) SPOREPERFORMANCE$FRAMES.remove();
        if (!frame.measure()) return;
        long elapsed = System.nanoTime() - frame.started();
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(frame.entity().getType());
        String id = key == null ? "unknown" : key.getPath();
        PerformanceMetrics.increment("ai_refactor.entity_tick.calls." + id);
        PerformanceMetrics.add("ai_refactor.entity_tick.nanos." + id, elapsed);
        if (elapsed >= PerformanceConfig.DIAGNOSTICS_AI_SLOW_ENTITY_MICROS.get() * 1_000L) {
            PerformanceMetrics.increment("ai_refactor.entity_tick.slow." + id);
            if (DebugTrace.enabled(DebugTrace.Category.GOAL))
                DebugTrace.event(DebugTrace.Category.GOAL, (ServerLevel) (Object) this, DebugTrace.trace(entity), entity,
                        "slow_entity_tick", "elapsedMicros=" + (elapsed / 1_000L) + ",thresholdMicros="
                                + PerformanceConfig.DIAGNOSTICS_AI_SLOW_ENTITY_MICROS.get());
        }
    }
}
