package com.arxyt.sporeperformance.world;

import com.Harbinger.Spore.SBlockEntities.LivingStructureBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stores positions only, never Level or BlockEntity references. Block entity lifecycle mixins keep
 * it current; queries validate entries so a stale lifecycle callback cannot award phantom kills.
 */
public final class StructureBlockIndex {
    public static final StructureBlockIndex INSTANCE = new StructureBlockIndex();
    private final Map<ResourceKey<Level>, Map<Long, Set<Long>>> positionsByDimension = new HashMap<>();

    public synchronized void add(BlockEntity entity) {
        if (!(entity instanceof LivingStructureBlocks) || !(entity.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = entity.getBlockPos();
        positionsByDimension.computeIfAbsent(level.dimension(), ignored -> new HashMap<>())
                .computeIfAbsent(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), ignored -> new HashSet<>())
                .add(pos.asLong());
    }

    @SubscribeEvent
    public synchronized void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        for (BlockEntity entity : chunk.getBlockEntities().values()) add(entity);
    }

    @SubscribeEvent
    public synchronized void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        for (BlockEntity entity : chunk.getBlockEntities().values()) remove(entity);
    }

    public synchronized void remove(BlockEntity entity) {
        if (!(entity instanceof LivingStructureBlocks) || !(entity.getLevel() instanceof ServerLevel level)) return;
        remove(level, entity.getBlockPos().asLong());
    }

    private void remove(ServerLevel level, long packedPos) {
        Map<Long, Set<Long>> byChunk = positionsByDimension.get(level.dimension());
        if (byChunk == null) return;
        long chunk = ChunkPos.asLong(BlockPos.getX(packedPos) >> 4, BlockPos.getZ(packedPos) >> 4);
        Set<Long> positions = byChunk.get(chunk);
        if (positions == null) return;
        positions.remove(packedPos);
        if (positions.isEmpty()) byChunk.remove(chunk);
        if (byChunk.isEmpty()) positionsByDimension.remove(level.dimension());
    }

    public synchronized List<LivingStructureBlocks> find(ServerLevel level, BlockPos center, int halfExtent) {
        Map<Long, Set<Long>> byChunk = positionsByDimension.get(level.dimension());
        if (byChunk == null || byChunk.isEmpty()) return Collections.emptyList();
        int minX = (center.getX() - halfExtent) >> 4;
        int maxX = (center.getX() + halfExtent) >> 4;
        int minZ = (center.getZ() - halfExtent) >> 4;
        int maxZ = (center.getZ() + halfExtent) >> 4;
        List<LivingStructureBlocks> found = new ArrayList<>();
        List<Long> stale = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Set<Long> entries = byChunk.get(ChunkPos.asLong(x, z));
                if (entries == null) continue;
                for (long packed : entries) {
                    BlockPos pos = BlockPos.of(packed);
                    if (Math.abs(pos.getX() - center.getX()) > halfExtent
                            || Math.abs(pos.getY() - center.getY()) > halfExtent
                            || Math.abs(pos.getZ() - center.getZ()) > halfExtent) continue;
                    BlockEntity entity = level.getBlockEntity(pos);
                    if (entity instanceof LivingStructureBlocks structure) found.add(structure);
                    else stale.add(packed);
                }
            }
        }
        stale.forEach(packed -> remove(level, packed));
        return found;
    }

    public synchronized void clear() { positionsByDimension.clear(); }
}
