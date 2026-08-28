package com.arxyt.sporeperformance.client.render;

import com.Harbinger.Spore.Client.Special.BaseInfectedRenderer;
import com.Harbinger.Spore.Core.Seffects;
import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Optional;

/** Defers expensive illusion proxy creation and caches only immutable registry lookups. */
public final class IllusionRenderOptimizer {
    private static final BoundedLruCache<String, Optional<EntityType<?>>> ENTITY_TYPES = new BoundedLruCache<>(256);

    @SuppressWarnings("rawtypes")
    public static Entity createFormForRender(BaseInfectedRenderer renderer, Infected infected) {
        if (PerformanceConfig.CLIENT_DEFER_ILLUSION_ENTITY_CREATION.get() && !illusionRequired(infected)) {
            ClientRenderMetrics.increment("illusion.entity_creation_avoided");
            return null;
        }
        ClientRenderMetrics.increment("illusion.entity_creation_requested");
        if (!PerformanceConfig.CLIENT_CACHE_ILLUSION_ENTITY_TYPES.get()) return renderer.getForm(infected);
        String origin = infected.getOrigin();
        Optional<EntityType<?>> type = ENTITY_TYPES.computeIfAbsent(origin, IllusionRenderOptimizer::resolveType);
        if (type.isEmpty()) return null;
        ClientRenderMetrics.increment("illusion.entity_type_cache_used");
        return type.get().create(infected.level());
    }

    public static boolean illusionRequired(Infected infected) {
        if (!(Minecraft.getInstance().getCameraEntity() instanceof Player player)) return false;
        MobEffectInstance madness = player.getEffect(Seffects.MADNESS.get());
        return IllusionPolicy.required(madness == null ? -1 : madness.getAmplifier(), player.distanceToSqr(infected));
    }

    public static int cacheSize() {
        return ENTITY_TYPES.size();
    }

    public static void clear() {
        ENTITY_TYPES.clear();
    }

    private static Optional<EntityType<?>> resolveType(String origin) {
        ResourceLocation location = ResourceLocation.tryParse(origin);
        return location == null ? Optional.empty() : Optional.ofNullable(ForgeRegistries.ENTITY_TYPES.getValue(location));
    }

    private IllusionRenderOptimizer() {}
}
