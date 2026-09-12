package com.arxyt.sporeperformance.world;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.StackWalker.StackFrame;
import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns admission, attribution and expiry of short-lived terrain block entities.
 *
 * <p>Vanilla FallingBlockEntity has no source field, so admission is reserved at the static
 * {@code FallingBlockEntity.fall} boundary before that method removes the source block. The
 * subsequent join event consumes the reservation and records only UUID, source, spawn chunk and
 * tick. This prevents a rejected block from deleting terrain and avoids retaining entities or
 * unloaded levels.</p>
 */
public final class TransientBlockEntityRuntime {
    public enum FallingSource {
        FOLIAGE,
        CALAMITY_BIOMASS,
        HOWITZER,
        HOHLFRESSER,
        THROWN_BLOCK,
        CORPSE,
        SPORE_OTHER,
        OTHER
    }

    public static final TransientBlockEntityRuntime INSTANCE = new TransientBlockEntityRuntime();
    private static final StackWalker STACK_WALKER = StackWalker.getInstance();
    private final Map<ServerLevel, State> levels = new IdentityHashMap<>();

    /** Called from the static fall mixin before vanilla removes the source block. */
    public synchronized boolean reserveFalling(Level level, BlockPos pos, BlockState block) {
        if (!(level instanceof ServerLevel server) || !PerformanceConfig.LIMIT_FALLING_BLOCKS_ENABLED.get()) return true;
        State state = levels.computeIfAbsent(server, ignored -> new State());
        long now = server.getGameTime();
        state.prunePending(now);
        FallingSource source = classify(block);
        long chunk = chunk(pos);
        if (!state.canReserve(source, chunk)) {
            // Hohlfresser removes its source block immediately before calling fall(). Other
            // known Spore callers delegate removal to FallingBlockEntity itself. Restore only
            // this pre-removed source so a quota rejection cannot become silent terrain loss.
            if (source == FallingSource.HOHLFRESSER && server.getBlockState(pos).isAir()) {
                server.setBlock(pos, block, 3);
            }
            state.rejected++;
            PerformanceMetrics.increment("transient_blocks.falling_rejected." + source.name().toLowerCase(java.util.Locale.ROOT));
            return false;
        }
        state.reserve(source, chunk, now);
        PerformanceMetrics.increment("transient_blocks.falling_reserved." + source.name().toLowerCase(java.util.Locale.ROOT));
        return true;
    }

    /** Limits our own Stahl visual block effects before they are instantiated. */
    public synchronized boolean allowRising(ServerLevel level, BlockPos pos) {
        if (!PerformanceConfig.LIMIT_RISING_BLOCKS_ENABLED.get()) return true;
        State state = levels.computeIfAbsent(level, ignored -> new State());
        long chunk = chunk(pos);
        if (state.rising.size() >= PerformanceConfig.LIMIT_RISING_BLOCKS_GLOBAL.get()
                || state.risingByChunk.getOrDefault(chunk, 0) >= PerformanceConfig.LIMIT_RISING_BLOCKS_PER_CHUNK.get()) {
            state.risingRejected++;
            PerformanceMetrics.increment("transient_blocks.rising_rejected");
            return false;
        }
        return true;
    }

    public synchronized boolean shouldExpireFalling(FallingBlockEntity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !PerformanceConfig.LIMIT_FALLING_BLOCKS_ENABLED.get()) return false;
        State state = state(level);
        Attribution attribution = state.falling.get(entity.getUUID());
        if (attribution == null) {
            // Chunk entity deserialization is not guaranteed to emit the normal join event on
            // every Forge build. Claim legacy entities on their first tick so a pre-existing
            // pile cannot bypass the cap or live forever after an upgrade.
            FallingSource source = classify(entity.getBlockState());
            long currentChunk = chunk(entity.blockPosition());
            if (!state.canReserve(source, currentChunk)) {
                state.rejected++;
                PerformanceMetrics.increment("transient_blocks.falling_legacy_overflow_discarded");
                return true;
            }
            attribution = new Attribution(source, currentChunk, level.getGameTime());
            state.addFalling(entity.getUUID(), attribution);
            PerformanceMetrics.increment("transient_blocks.falling_legacy_tracked");
        }
        return level.getGameTime() - attribution.createdTick >= PerformanceConfig.LIMIT_FALLING_BLOCKS_TTL_TICKS.get();
    }

    public synchronized boolean shouldExpireRising(Entity entity) {
        if (!(entity.level() instanceof ServerLevel level) || !PerformanceConfig.LIMIT_RISING_BLOCKS_ENABLED.get()) return false;
        State state = state(level);
        RisingAttribution attribution = state.rising.get(entity.getUUID());
        if (attribution == null) {
            long currentChunk = chunk(entity.blockPosition());
            if (state.rising.size() >= PerformanceConfig.LIMIT_RISING_BLOCKS_GLOBAL.get()
                    || state.risingByChunk.getOrDefault(currentChunk, 0) >= PerformanceConfig.LIMIT_RISING_BLOCKS_PER_CHUNK.get()) {
                state.risingRejected++;
                PerformanceMetrics.increment("transient_blocks.rising_legacy_overflow_discarded");
                return true;
            }
            attribution = new RisingAttribution(currentChunk, level.getGameTime());
            state.addRising(entity.getUUID(), attribution);
        }
        return attribution != null && level.getGameTime() - attribution.createdTick >= PerformanceConfig.LIMIT_RISING_BLOCKS_TTL_TICKS.get();
    }

    @SubscribeEvent
    public synchronized void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        if (entity instanceof FallingBlockEntity falling) {
            State state = state(level);
            long now = level.getGameTime();
            state.prunePending(now);
            long currentChunk = chunk(falling.blockPosition());
            Pending pending = state.consumePending(currentChunk);
            FallingSource source = pending == null ? classify(falling.getBlockState()) : pending.source;
            long spawnChunk = pending == null ? currentChunk : pending.chunk;
            if (pending == null && !state.canReserve(source, spawnChunk)) {
                event.setCanceled(true);
                state.rejected++;
                PerformanceMetrics.increment("transient_blocks.falling_join_rejected");
                return;
            }
            state.addFalling(falling.getUUID(), new Attribution(source, spawnChunk, now));
            PerformanceMetrics.increment("transient_blocks.falling_active." + source.name().toLowerCase(java.util.Locale.ROOT));
            return;
        }
        if (isRisingBlock(entity)) {
            State state = state(level);
            long currentChunk = chunk(entity.blockPosition());
            if (PerformanceConfig.LIMIT_RISING_BLOCKS_ENABLED.get()
                    && (state.rising.size() >= PerformanceConfig.LIMIT_RISING_BLOCKS_GLOBAL.get()
                    || state.risingByChunk.getOrDefault(currentChunk, 0) >= PerformanceConfig.LIMIT_RISING_BLOCKS_PER_CHUNK.get())) {
                event.setCanceled(true);
                state.risingRejected++;
                PerformanceMetrics.increment("transient_blocks.rising_join_rejected");
                return;
            }
            state.addRising(entity.getUUID(), new RisingAttribution(currentChunk, level.getGameTime()));
        }
    }

    @SubscribeEvent
    public synchronized void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        State state = levels.get(level);
        if (state == null) return;
        Entity entity = event.getEntity();
        if (entity instanceof FallingBlockEntity) state.removeFalling(entity.getUUID());
        else if (isRisingBlock(entity)) state.removeRising(entity.getUUID());
    }

    @SubscribeEvent
    public synchronized void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        State state = levels.get(level);
        if (state == null || level.getGameTime() % 20L != 0L) return;
        state.prunePending(level.getGameTime());
        state.falling.entrySet().removeIf(entry -> {
            if (level.getEntity(entry.getKey()) != null) return false;
            state.decrementFalling(entry.getValue());
            return true;
        });
        state.rising.entrySet().removeIf(entry -> {
            if (level.getEntity(entry.getKey()) != null) return false;
            state.decrementRising(entry.getValue());
            return true;
        });
    }

    @SubscribeEvent
    public synchronized void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) levels.remove(level);
    }

    public synchronized List<String> statusLines() {
        return levels.entrySet().stream().map(entry -> {
            State state = entry.getValue();
            return "Transient blocks " + entry.getKey().dimension().location()
                    + ": falling=" + state.falling.size() + ", pending=" + state.pendingTotal
                    + ", chunks=" + state.fallingByChunk.size() + ", sources=" + state.sources
                    + ", rejected=" + state.rejected + ", rising=" + state.rising.size()
                    + ", risingRejected=" + state.risingRejected;
        }).toList();
    }

    public synchronized void clear() {
        levels.clear();
    }

    private State state(ServerLevel level) {
        return levels.computeIfAbsent(level, ignored -> new State());
    }

    private static FallingSource classify(BlockState block) {
        return STACK_WALKER.walk(stream -> stream.map(StackFrame::getClassName)
                .map(TransientBlockEntityRuntime::sourceForClass)
                .filter(source -> source != null)
                .findFirst()
                .orElseGet(() -> isSporeBlock(block) ? FallingSource.SPORE_OTHER : FallingSource.OTHER));
    }

    static FallingSource sourceForClass(String name) {
        if (name.endsWith("FoliageSpread")) return FallingSource.FOLIAGE;
        if (name.endsWith("ThrownBlockProjectile")) return FallingSource.THROWN_BLOCK;
        if (name.endsWith("CorpseEntity")) return FallingSource.CORPSE;
        if (name.endsWith("Hohlfresser")) return FallingSource.HOHLFRESSER;
        if (name.endsWith("Howitzer")) return FallingSource.HOWITZER;
        if (name.endsWith("Calamity")) return FallingSource.CALAMITY_BIOMASS;
        return null;
    }

    private static boolean isSporeBlock(BlockState block) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block.getBlock());
        return key != null && "spore".equals(key.getNamespace());
    }

    private static boolean isRisingBlock(Entity entity) {
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key != null && (("spore_performance".equals(key.getNamespace()) && "stahl_rising_block".equals(key.getPath()))
                || ("exhuashan_sporeai_fix".equals(key.getNamespace()) && "rising_block".equals(key.getPath())));
    }

    private static long chunk(BlockPos pos) {
        return net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private record Pending(FallingSource source, long chunk, long reservedTick) {}
    private record Attribution(FallingSource source, long chunk, long createdTick) {}
    private record RisingAttribution(long chunk, long createdTick) {}

    private static final class State {
        private final Map<UUID, Attribution> falling = new HashMap<>();
        private final Map<Long, Integer> fallingByChunk = new HashMap<>();
        private final EnumMap<FallingSource, Integer> sources = new EnumMap<>(FallingSource.class);
        private final Map<Long, ArrayDeque<Pending>> pending = new HashMap<>();
        private final Map<Long, Integer> pendingByChunk = new HashMap<>();
        private final EnumMap<FallingSource, Integer> pendingSources = new EnumMap<>(FallingSource.class);
        private final Map<UUID, RisingAttribution> rising = new HashMap<>();
        private final Map<Long, Integer> risingByChunk = new HashMap<>();
        private int pendingTotal;
        private long rejected;
        private long risingRejected;

        private boolean canReserve(FallingSource source, long chunk) {
            int total = falling.size() + pendingTotal;
            int local = fallingByChunk.getOrDefault(chunk, 0) + pendingByChunk.getOrDefault(chunk, 0);
            int bySource = sources.getOrDefault(source, 0) + pendingSources.getOrDefault(source, 0);
            return total < PerformanceConfig.LIMIT_FALLING_BLOCKS_GLOBAL.get()
                    && local < PerformanceConfig.LIMIT_FALLING_BLOCKS_PER_CHUNK.get()
                    && bySource < PerformanceConfig.LIMIT_FALLING_BLOCKS_PER_SOURCE.get();
        }

        private void reserve(FallingSource source, long chunk, long now) {
            pending.computeIfAbsent(chunk, ignored -> new ArrayDeque<>()).addLast(new Pending(source, chunk, now));
            pendingByChunk.merge(chunk, 1, Integer::sum);
            pendingSources.merge(source, 1, Integer::sum);
            pendingTotal++;
        }

        private Pending consumePending(long chunk) {
            ArrayDeque<Pending> values = pending.get(chunk);
            if (values == null || values.isEmpty()) return null;
            Pending result = values.removeFirst();
            if (values.isEmpty()) pending.remove(chunk);
            decrement(pendingByChunk, chunk);
            decrement(pendingSources, result.source);
            pendingTotal--;
            return result;
        }

        private void prunePending(long now) {
            var iterator = pending.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                ArrayDeque<Pending> values = entry.getValue();
                while (!values.isEmpty() && now - values.peekFirst().reservedTick > 2L) {
                    Pending removed = values.removeFirst();
                    decrement(pendingByChunk, removed.chunk);
                    decrement(pendingSources, removed.source);
                    pendingTotal--;
                }
                if (values.isEmpty()) iterator.remove();
            }
        }

        private void addFalling(UUID id, Attribution attribution) {
            Attribution previous = falling.put(id, attribution);
            if (previous != null) decrementFalling(previous);
            fallingByChunk.merge(attribution.chunk, 1, Integer::sum);
            sources.merge(attribution.source, 1, Integer::sum);
        }

        private void removeFalling(UUID id) {
            Attribution attribution = falling.remove(id);
            if (attribution != null) decrementFalling(attribution);
        }

        private void decrementFalling(Attribution attribution) {
            decrement(fallingByChunk, attribution.chunk);
            decrement(sources, attribution.source);
        }

        private void addRising(UUID id, RisingAttribution attribution) {
            RisingAttribution previous = rising.put(id, attribution);
            if (previous != null) decrementRising(previous);
            risingByChunk.merge(attribution.chunk, 1, Integer::sum);
        }

        private void removeRising(UUID id) {
            RisingAttribution attribution = rising.remove(id);
            if (attribution != null) decrementRising(attribution);
        }

        private void decrementRising(RisingAttribution attribution) {
            decrement(risingByChunk, attribution.chunk);
        }

        private static <K> void decrement(Map<K, Integer> values, K key) {
            int next = values.getOrDefault(key, 0) - 1;
            if (next <= 0) values.remove(key);
            else values.put(key, next);
        }
    }

    private TransientBlockEntityRuntime() {}
}
