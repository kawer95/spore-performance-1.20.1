package com.arxyt.sporeperformance.compat;

import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Cached static-map readers that turn sporesrp's levels×records loops into known-dimension loops. */
public final class SporeSrpLevelBuckets {
    private static final ClassValue<Optional<VarHandle>> DATA_MAPS = new ClassValue<>() {
        @Override protected Optional<VarHandle> computeValue(Class<?> type) {
            try {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) && Map.class.isAssignableFrom(field.getType())
                            && (field.getName().equals("skillDataMap") || field.getName().equals("protoDataMap"))) {
                        return Optional.of(MethodHandles.privateLookupIn(type, MethodHandles.lookup()).unreflectVarHandle(field));
                    }
                }
            } catch (IllegalAccessException exception) {
                SporePerformance.LOGGER.warn("sporesrp level bucket reflection disabled for {}", type.getName(), exception);
            }
            return Optional.empty();
        }
    };
    private static final ClassValue<Optional<VarHandle>> BUILDER_MAPS = new ClassValue<>() {
        @Override protected Optional<VarHandle> computeValue(Class<?> type) {
            try {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) && Map.class.isAssignableFrom(field.getType())
                            && field.getName().equals("builderCache")) {
                        return Optional.of(MethodHandles.privateLookupIn(type, MethodHandles.lookup()).unreflectVarHandle(field));
                    }
                }
            } catch (IllegalAccessException exception) {
                SporePerformance.LOGGER.warn("sporesrp Builder level bucket reflection disabled for {}", type.getName(), exception);
            }
            return Optional.empty();
        }
    };

    public static Iterable<ServerLevel> levelsFor(MinecraftServer server, Object handler) {
        if (!PerformanceConfig.SAFE_SPORESRP_DIMENSION_GUARDS.get() || !OptionalCompatProbe.sporesrpReady()) return server.getAllLevels();
        VarHandle handle = DATA_MAPS.get(handler.getClass()).orElse(null);
        if (handle == null || !(handle.get() instanceof Map<?, ?> records) || records.isEmpty()) return server.getAllLevels();
        Set<ResourceKey<Level>> dimensions = new HashSet<>();
        for (Object key : records.keySet()) {
            if (!(key instanceof UUID id)) return server.getAllLevels();
            ResourceKey<Level> dimension = DimensionEntityIndex.INSTANCE.knownDimension(id);
            if (dimension == null) return server.getAllLevels();
            dimensions.add(dimension);
        }
        Collection<ServerLevel> result = new ArrayList<>(dimensions.size());
        for (ResourceKey<Level> dimension : dimensions) {
            ServerLevel level = server.getLevel(dimension);
            if (level != null) result.add(level);
        }
        PerformanceMetrics.increment("sporesrp.level_bucketed_tick");
        return result;
    }

    /** Builder already owns a dimension-keyed UUID map; avoid creating empty sets for all other dimensions. */
    public static Iterable<ServerLevel> levelsForBuilders(MinecraftServer server, Object handler) {
        if (!PerformanceConfig.SAFE_SPORESRP_DIMENSION_GUARDS.get() || !OptionalCompatProbe.sporesrpReady()) return server.getAllLevels();
        VarHandle handle = BUILDER_MAPS.get(handler.getClass()).orElse(null);
        if (handle == null || !(handle.get() instanceof Map<?, ?> records)) return server.getAllLevels();
        Set<String> activeDimensions = new HashSet<>();
        for (Map.Entry<?, ?> entry : records.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof Set<?> entities)) return server.getAllLevels();
            if (!entities.isEmpty()) activeDimensions.add(key);
        }
        if (activeDimensions.isEmpty()) return List.of();
        Collection<ServerLevel> result = new ArrayList<>(activeDimensions.size());
        for (ServerLevel level : server.getAllLevels()) {
            if (activeDimensions.contains(level.dimension().location().toString())) result.add(level);
        }
        PerformanceMetrics.increment("sporesrp.builder_level_bucketed_tick");
        return result;
    }
    private SporeSrpLevelBuckets() {}
}
