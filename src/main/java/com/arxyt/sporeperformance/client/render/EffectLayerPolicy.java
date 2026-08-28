package com.arxyt.sporeperformance.client.render;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** Squared-distance policy shared by all optional Spore effect-layer mixins. */
public final class EffectLayerPolicy {
    public enum Kind { EYE, TRANSLUCENT, EMISSIVE }

    public static boolean shouldCull(Entity entity, Kind kind) {
        if (entity == null) return false;
        SporeRenderClassifier.Category category = SporeRenderClassifier.category(entity);
        boolean enabled = enabled(category, kind);
        if (!enabled) {
            ClientRenderMetrics.increment("layer." + kind.name().toLowerCase(java.util.Locale.ROOT) + ".rendered");
            return false;
        }
        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (camera == null || camera == entity) return false;
        int distance = switch (category) {
            case CALAMITY -> PerformanceConfig.CLIENT_CALAMITY_EFFECT_DISTANCE.get();
            case ORGANOID -> PerformanceConfig.CLIENT_ORGANOID_EFFECT_DISTANCE.get();
            case HYPER -> PerformanceConfig.CLIENT_HYPER_EFFECT_DISTANCE.get();
            case PROTO -> PerformanceConfig.CLIENT_PROTO_EFFECT_DISTANCE.get();
            case NORMAL -> switch (kind) {
                    case EYE -> PerformanceConfig.CLIENT_EYE_RENDER_DISTANCE.get();
                    case TRANSLUCENT -> PerformanceConfig.CLIENT_TRANSLUCENT_RENDER_DISTANCE.get();
                    case EMISSIVE -> PerformanceConfig.CLIENT_EMISSIVE_RENDER_DISTANCE.get();
                };
        };
        boolean culled = camera.distanceToSqr(entity) > (double) distance * distance;
        if (culled) ClientRenderMetrics.increment("layer." + kind.name().toLowerCase(java.util.Locale.ROOT) + ".culled");
        else ClientRenderMetrics.increment("layer." + kind.name().toLowerCase(java.util.Locale.ROOT) + ".rendered");
        if (ClientRenderMetrics.enabled()) {
            ClientRenderMetrics.increment("layer." + category.name().toLowerCase(java.util.Locale.ROOT) + "."
                    + kind.name().toLowerCase(java.util.Locale.ROOT) + (culled ? ".culled" : ".rendered"));
        }
        return culled;
    }

    private static boolean enabled(SporeRenderClassifier.Category category, Kind kind) {
        return switch (category) {
            case CALAMITY -> PerformanceConfig.CLIENT_CALAMITY_EFFECT_CULL.get();
            case ORGANOID -> PerformanceConfig.CLIENT_ORGANOID_EFFECT_CULL.get();
            case HYPER -> PerformanceConfig.CLIENT_HYPER_EFFECT_CULL.get();
            case PROTO -> PerformanceConfig.CLIENT_PROTO_EFFECT_CULL.get();
            case NORMAL -> switch (kind) {
                case EYE -> PerformanceConfig.CLIENT_EYE_DISTANCE_CULL.get();
                case TRANSLUCENT -> PerformanceConfig.CLIENT_TRANSLUCENT_DISTANCE_CULL.get();
                case EMISSIVE -> PerformanceConfig.CLIENT_EMISSIVE_DISTANCE_CULL.get();
            };
        };
    }

    private EffectLayerPolicy() {}
}
