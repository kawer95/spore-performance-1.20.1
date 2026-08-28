package com.arxyt.sporeperformance.diagnostics;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Loaded-entity census for diagnosis only.  It is maintained by entity and chunk life-cycle
 * events, never scans chunks, and deliberately retains only UUID/type metadata instead of
 * Entity references.  Chunk unload is handled explicitly because it does not imply that the
 * entity has left its ServerLevel.
 */
public final class LoadedEntityCensus {
    public static final LoadedEntityCensus INSTANCE = new LoadedEntityCensus();
    private final Map<ServerLevel, State> levels = new IdentityHashMap<>();

    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) return;
        Entity entity = event.getEntity();
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (type == null) return;
        levels.computeIfAbsent(level, ignored -> new State()).put(entity.getUUID(), new Entry(
                type, entity instanceof LivingEntity, entity instanceof Mob,
                entity instanceof Projectile, entity instanceof ItemEntity, chunkKey(entity)));
    }

    @SubscribeEvent
    public void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        State state = levels.get(level);
        if (state != null) state.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onEnteringSection(EntityEvent.EnteringSection event) {
        if (!event.didChunkChange() || !(event.getEntity().level() instanceof ServerLevel level)) return;
        State state = levels.get(level);
        if (state != null) state.move(event.getEntity().getUUID(), event.getNewPos().x(), event.getNewPos().z());
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        State state = levels.get(level);
        if (state != null) state.removeChunk(event.getChunk().getPos().toLong());
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) levels.remove(level);
    }

    public List<String> statusLines(String namespace) {
        String normalized = namespace == null ? null : namespace.toLowerCase(Locale.ROOT);
        List<Map.Entry<ServerLevel, State>> dimensions = new ArrayList<>(levels.entrySet());
        dimensions.sort(Comparator.comparing(entry -> entry.getKey().dimension().location().toString()));
        List<String> lines = new ArrayList<>();
        for (Map.Entry<ServerLevel, State> dimension : dimensions) {
            Counts counts = new Counts();
            Map<ResourceLocation, Integer> types = new java.util.HashMap<>();
            for (Entry entry : dimension.getValue().entities.values()) {
                if (normalized != null && !normalized.equals(entry.type.getNamespace())) continue;
                counts.add(entry);
                types.merge(entry.type, 1, Integer::sum);
            }
            lines.add("Loaded census " + dimension.getKey().dimension().location()
                    + (normalized == null ? "" : " namespace=" + normalized)
                    + ": all=" + counts.all + ", living=" + counts.living + ", spore=" + counts.spore
                    + ", sporeMobCap=" + counts.sporeMobs + ", sporeProjectiles=" + counts.sporeProjectiles
                    + ", bile=" + counts.bile + ", items=" + counts.items);
            if (!types.isEmpty()) lines.add("Loaded census top types: " + topTypes(types));
        }
        if (lines.isEmpty()) lines.add("Loaded census: no active ServerLevel has reported entity join events yet.");
        return lines;
    }

    public void clear() { levels.clear(); }

    private static String topTypes(Map<ResourceLocation, Integer> types) {
        return types.entrySet().stream()
                .sorted(Comparator.<Map.Entry<ResourceLocation, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(entry -> entry.getKey().toString()))
                .limit(16)
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + ", " + right).orElse("");
    }

    private static long chunkKey(Entity entity) {
        return net.minecraft.world.level.ChunkPos.asLong(entity.chunkPosition().x, entity.chunkPosition().z);
    }

    private static final class State {
        private final Map<UUID, Entry> entities = new java.util.HashMap<>();
        private final Map<Long, java.util.Set<UUID>> chunks = new java.util.HashMap<>();

        private void put(UUID id, Entry entry) {
            remove(id);
            entities.put(id, entry);
            chunks.computeIfAbsent(entry.chunk, ignored -> new java.util.HashSet<>()).add(id);
        }

        private void move(UUID id, int chunkX, int chunkZ) {
            Entry entry = entities.get(id);
            if (entry == null) return;
            long next = net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ);
            if (entry.chunk == next) return;
            remove(id);
            put(id, entry.withChunk(next));
        }

        private void remove(UUID id) {
            Entry entry = entities.remove(id);
            if (entry == null) return;
            java.util.Set<UUID> bucket = chunks.get(entry.chunk);
            if (bucket == null) return;
            bucket.remove(id);
            if (bucket.isEmpty()) chunks.remove(entry.chunk);
        }

        private void removeChunk(long chunk) {
            java.util.Set<UUID> bucket = chunks.get(chunk);
            if (bucket == null || bucket.isEmpty()) return;
            for (UUID id : new ArrayList<>(bucket)) remove(id);
        }
    }

    private record Entry(ResourceLocation type, boolean living, boolean mob, boolean projectile, boolean item, long chunk) {
        private Entry withChunk(long value) { return new Entry(type, living, mob, projectile, item, value); }
    }

    private static final class Counts {
        private int all, living, spore, sporeMobs, sporeProjectiles, bile, items;
        private void add(Entry entry) {
            ++all;
            if (entry.living) ++living;
            if (entry.item) ++items;
            if (!"spore".equals(entry.type.getNamespace())) return;
            ++spore;
            if (entry.mob) ++sporeMobs;
            if (entry.projectile) ++sporeProjectiles;
            if ("bile".equals(entry.type.getPath())) ++bile;
        }
    }

    private LoadedEntityCensus() {}
}
