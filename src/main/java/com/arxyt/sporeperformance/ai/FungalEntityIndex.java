package com.arxyt.sporeperformance.ai;

import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loaded-only, three-dimensional section index owned by one ServerLevel runtime. */
public final class FungalEntityIndex {
    private final Map<Long, Set<LivingEntity>> sections = new HashMap<>();
    /**
     * Reverse lookup for chunk unload.  EntityLeaveLevelEvent is not a chunk-life-cycle event:
     * an entity can stop ticking because its chunk unloads while still belonging to the same
     * ServerLevel.  Retaining it in {@link #locations} would make the "loaded" index grow for an
     * entire play session and, worse, retain the entity strongly.  Keep an explicit chunk bucket
     * so that unload removal is proportional to that chunk's entries, never to the whole level.
     */
    private final Map<Long, Set<LivingEntity>> chunks = new HashMap<>();
    private final Map<LivingEntity, Long> locations = new IdentityHashMap<>();

    public void add(LivingEntity entity) {
        long section = sectionKey(entity);
        Long old = locations.put(entity, section);
        if (old != null && old == section) return;
        if (old != null) removeFrom(old, entity);
        addTo(section, entity);
    }

    public void update(LivingEntity entity) {
        long current = sectionKey(entity);
        Long previous = locations.get(entity);
        if (previous == null) {
            add(entity);
        } else if (previous != current) {
            removeFrom(previous, entity);
            locations.put(entity, current);
            addTo(current, entity);
        }
    }

    public void remove(LivingEntity entity) {
        Long section = locations.remove(entity);
        if (section != null) removeFrom(section, entity);
    }

    /** Removes only entities in an unloading chunk; it never asks the level to load anything. */
    public int removeChunk(long chunk) {
        Set<LivingEntity> bucket = chunks.get(chunk);
        if (bucket == null || bucket.isEmpty()) return 0;
        List<LivingEntity> removed = new ArrayList<>(bucket);
        for (LivingEntity entity : removed) remove(entity);
        return removed.size();
    }

    public <T extends LivingEntity> List<T> query(AABB bounds, Class<T> type, Entity except) {
        int minX = SectionPos.blockToSectionCoord(Mth.floor(bounds.minX));
        int minY = SectionPos.blockToSectionCoord(Mth.floor(bounds.minY));
        int minZ = SectionPos.blockToSectionCoord(Mth.floor(bounds.minZ));
        int maxX = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxX));
        int maxY = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxY));
        int maxZ = SectionPos.blockToSectionCoord(Mth.floor(bounds.maxZ));
        List<T> result = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Set<LivingEntity> bucket = sections.get(SectionPos.asLong(x, y, z));
                    if (bucket == null) continue;
                    for (LivingEntity candidate : bucket) {
                        if (candidate == except || candidate.isRemoved() || !candidate.isAlive()
                                || !type.isInstance(candidate) || !bounds.intersects(candidate.getBoundingBox())) continue;
                        result.add(type.cast(candidate));
                    }
                }
            }
        }
        return result;
    }

    public int size() { return locations.size(); }
    public int sectionCount() { return sections.size(); }
    public void clear() { sections.clear(); chunks.clear(); locations.clear(); }

    private void removeFrom(long section, LivingEntity entity) {
        Set<LivingEntity> bucket = sections.get(section);
        if (bucket != null) {
            bucket.remove(entity);
            if (bucket.isEmpty()) sections.remove(section);
        }
        long chunk = chunkKey(section);
        Set<LivingEntity> chunkBucket = chunks.get(chunk);
        if (chunkBucket != null) {
            chunkBucket.remove(entity);
            if (chunkBucket.isEmpty()) chunks.remove(chunk);
        }
    }

    private void addTo(long section, LivingEntity entity) {
        sections.computeIfAbsent(section, ignored -> new LinkedHashSet<>()).add(entity);
        chunks.computeIfAbsent(chunkKey(section), ignored -> new LinkedHashSet<>()).add(entity);
    }

    private static long chunkKey(long section) {
        return ChunkPos.asLong(SectionPos.x(section), SectionPos.z(section));
    }

    private static long sectionKey(Entity entity) {
        return SectionPos.asLong(SectionPos.blockToSectionCoord(Mth.floor(entity.getX())),
                SectionPos.blockToSectionCoord(Mth.floor(entity.getY())),
                SectionPos.blockToSectionCoord(Mth.floor(entity.getZ())));
    }
}
