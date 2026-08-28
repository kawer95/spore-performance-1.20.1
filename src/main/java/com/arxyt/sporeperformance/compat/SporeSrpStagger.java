package com.arxyt.sporeperformance.compat;

import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraftforge.event.TickEvent;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * UUID-phase sharding for expensive sporesrp handler loops.  Data remains in the original
 * maps/sets and all native cooldown values stay authoritative; only the check turn is deferred.
 */
public final class SporeSrpStagger {
    public enum Kind { PROTO, FULL_HIVEMIND, BUILDER }

    private static final ThreadLocal<Long> CURRENT_TICK = ThreadLocal.withInitial(() -> Long.MIN_VALUE);
    private static final ClassValue<Optional<VarHandle>> UUID_FIELDS = new ClassValue<>() {
        @Override protected Optional<VarHandle> computeValue(Class<?> type) {
            try {
                for (Field field : type.getDeclaredFields()) {
                    if (field.getType() == UUID.class && (field.getName().equals("protoUuid") || field.getName().equals("hivemindUuid"))) {
                        return Optional.of(MethodHandles.privateLookupIn(type, MethodHandles.lookup()).unreflectVarHandle(field));
                    }
                }
            } catch (IllegalAccessException exception) {
                SporePerformance.LOGGER.warn("sporesrp UUID staggering disabled for {}", type.getName(), exception);
            }
            return Optional.empty();
        }
    };

    public static void begin(TickEvent.ServerTickEvent event) { CURRENT_TICK.set((long) event.getServer().getTickCount()); }
    public static void clear() { CURRENT_TICK.remove(); }

    public static Collection<?> dataValues(Map<?, ?> source, Kind kind) {
        int factor = factor(kind);
        if (factor == 1) return source.values();
        List<Object> result = new ArrayList<>();
        for (Object value : source.values()) {
            UUID uuid = uuidOf(value);
            if (uuid == null) return source.values(); // fail closed if an upstream data class changes
            if (belongsToCurrentTurn(uuid, kind, factor)) result.add(value);
        }
        PerformanceMetrics.increment("sporesrp." + kind.name().toLowerCase() + "_uuid_staggered_tick");
        return result;
    }

    public static Iterator<UUID> builderIterator(Set<UUID> source) {
        int factor = factor(Kind.BUILDER);
        if (factor == 1) return source.iterator();
        return new Iterator<>() {
            private final Iterator<UUID> delegate = source.iterator();
            private UUID next;
            private boolean prepared;
            private boolean removable;

            @Override public boolean hasNext() {
                if (!prepared) prepare();
                return next != null;
            }

            @Override public UUID next() {
                if (!hasNext()) throw new java.util.NoSuchElementException();
                UUID value = next;
                prepared = false;
                removable = true;
                return value;
            }

            @Override public void remove() {
                if (!removable) throw new IllegalStateException();
                delegate.remove();
                removable = false;
            }

            private void prepare() {
                prepared = true;
                while (delegate.hasNext()) {
                    UUID candidate = delegate.next();
                    if (belongsToCurrentTurn(candidate, Kind.BUILDER, factor)) {
                        next = candidate;
                        return;
                    }
                }
                next = null;
            }
        };
    }

    private static UUID uuidOf(Object data) {
        VarHandle field = UUID_FIELDS.get(data.getClass()).orElse(null);
        Object uuid = field == null ? null : field.get(data);
        return uuid instanceof UUID value ? value : null;
    }

    private static boolean belongsToCurrentTurn(UUID uuid, Kind kind, int factor) {
        long tick = CURRENT_TICK.get();
        int cadence = switch (kind) {
            case PROTO -> 2;
            case FULL_HIVEMIND -> 4;
            case BUILDER -> 10;
        };
        long turn = tick == Long.MIN_VALUE ? 0L : Math.floorDiv(tick, cadence);
        return Math.floorMod(uuid.hashCode(), factor) == Math.floorMod(turn, factor);
    }

    private static int factor(Kind kind) {
        return switch (kind) {
            case PROTO -> PerformanceConfig.AGGRESSIVE_SPORESRP_PROTO_STAGGER.get();
            case FULL_HIVEMIND -> PerformanceConfig.AGGRESSIVE_SPORESRP_FULL_HIVEMIND_STAGGER.get();
            case BUILDER -> PerformanceConfig.AGGRESSIVE_SPORESRP_BUILDER_STAGGER.get();
        };
    }
    private SporeSrpStagger() {}
}
