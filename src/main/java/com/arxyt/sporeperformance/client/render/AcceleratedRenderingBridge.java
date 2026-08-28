package com.arxyt.sporeperformance.client.render;

import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.fml.ModList;

/** Optional, cached bridge to AcceleratedRendering without a runtime class dependency. */
public final class AcceleratedRenderingBridge {
    public enum State { ACTIVE, SKIPPED, INCOMPATIBLE }

    private static volatile AcceleratedRenderingSignatures.Handles handles = resolve();
    private static volatile State state = handles == null
            ? (ModList.get().isLoaded("acceleratedrendering") ? State.INCOMPATIBLE : State.SKIPPED)
            : State.ACTIVE;
    private static volatile boolean failureLogged;

    public static VertexConsumer getBuffer(MultiBufferSource source, RenderType type, boolean emissive) {
        boolean enabled = emissive
                ? PerformanceConfig.CLIENT_ACCELERATE_EMISSIVE_LAYERS.get()
                : PerformanceConfig.CLIENT_ACCELERATE_TRANSLUCENT_LAYERS.get();
        AcceleratedRenderingSignatures.Handles current = handles;
        if (!enabled || current == null || !PerformanceConfig.CLIENT_ACCELERATED_RENDERING_AUTO_DETECT.get()) {
            ClientRenderMetrics.increment("accelerated_rendering.fallback");
            return source.getBuffer(type);
        }
        try {
            if (!(boolean) current.isLoaded().invokeExact()) return source.getBuffer(type);
            current.forceTranslucent().invokeExact();
        } catch (Throwable throwable) {
            disable(throwable);
            return source.getBuffer(type);
        }
        try {
            VertexConsumer consumer = source.getBuffer(type);
            ClientRenderMetrics.increment(emissive
                    ? "accelerated_rendering.emissive_buffer"
                    : "accelerated_rendering.translucent_buffer");
            return consumer;
        } finally {
            try {
                current.resetTranslucent().invokeExact();
            } catch (Throwable throwable) {
                disable(throwable);
            }
        }
    }

    public static State state() {
        return state;
    }

    public static boolean ready() {
        return handles != null && state == State.ACTIVE;
    }

    private static AcceleratedRenderingSignatures.Handles resolve() {
        if (!ModList.get().isLoaded("acceleratedrendering")) return null;
        try {
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            Class<?> core = Class.forName("com.github.argon4w.acceleratedrendering.core.CoreFeature", false, loader);
            return AcceleratedRenderingSignatures.resolve(core);
        } catch (ReflectiveOperationException | LinkageError exception) {
            SporePerformance.LOGGER.warn("AcceleratedRendering signature is incompatible; Spore layer bridge disabled", exception);
            return null;
        }
    }

    private static void disable(Throwable throwable) {
        handles = null;
        state = State.INCOMPATIBLE;
        if (!failureLogged) {
            failureLogged = true;
            SporePerformance.LOGGER.warn("AcceleratedRendering Spore layer bridge failed and was disabled", throwable);
        }
    }

    private AcceleratedRenderingBridge() {}
}
