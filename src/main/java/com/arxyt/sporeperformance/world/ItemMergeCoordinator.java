package com.arxyt.sporeperformance.world;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Batches nearby item merging so ItemEntity does not perform one world query per stack. */
public final class ItemMergeCoordinator {
    public static final ItemMergeCoordinator INSTANCE = new ItemMergeCoordinator();
    private final Map<ServerLevel, State> states = new IdentityHashMap<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onToss(ItemTossEvent event) {
        if (event.getEntity() instanceof ManagedItemEntity managed) {
            managed.sporeperformance$setPlayerDropped(true);
            managed.sporeperformance$setLifetimeConfigured(false);
            ItemOptimizationPolicy.applyLifetime(event.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ItemEntity item)) return;
        ItemOptimizationPolicy.applyLifetime(item);
        if (ItemOptimizationPolicy.managedForMerge(item)) state(level).items.add(item);
    }

    @SubscribeEvent
    public void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ItemEntity item)) return;
        State state = states.get(level);
        if (state != null) state.items.remove(item);
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)
                || !PerformanceConfig.ITEM_MERGE_ENABLED.get()) return;
        int interval = PerformanceConfig.ITEM_MERGE_INTERVAL.get();
        if (Math.floorMod(level.getGameTime(), interval) != 0) return;
        State state = states.get(level);
        if (state == null || state.items.size() < 2) return;
        runPass(level, state);
    }

    private void runPass(ServerLevel level, State state) {
        long deadline = System.nanoTime() + PerformanceConfig.ITEM_MERGE_TIME_BUDGET_MICROS.get() * 1_000L;
        int budget = PerformanceConfig.ITEM_MERGE_ENTITY_BUDGET.get();
        List<ItemEntity> items = new ArrayList<>(state.items.size());
        Map<Long, List<ItemEntity>> buckets = new HashMap<>();
        state.items.removeIf(item -> item.isRemoved() || item.level() != level || !ItemOptimizationPolicy.managedForMerge(item));
        for (ItemEntity item : state.items) {
            items.add(item);
            buckets.computeIfAbsent(chunkKey(item), ignored -> new ArrayList<>()).add(item);
        }
        if (items.size() < 2) return;

        int start = Math.floorMod(state.cursor, items.size());
        int visited = 0;
        int mergedEntities = 0;
        double radiusSqr = Mth.square(PerformanceConfig.ITEM_MERGE_RADIUS.get());
        while (visited < budget && visited < items.size() && System.nanoTime() < deadline) {
            ItemEntity anchor = items.get((start + visited) % items.size());
            ++visited;
            if (anchor.isRemoved() || anchor.getItem().isEmpty()) continue;
            int chunkX = Mth.floor(anchor.getX()) >> 4;
            int chunkZ = Mth.floor(anchor.getZ()) >> 4;
            for (int dx = -1; dx <= 1 && !anchor.isRemoved(); ++dx) {
                for (int dz = -1; dz <= 1 && !anchor.isRemoved(); ++dz) {
                    List<ItemEntity> candidates = buckets.get(net.minecraft.world.level.ChunkPos.asLong(chunkX + dx, chunkZ + dz));
                    if (candidates == null) continue;
                    for (ItemEntity other : candidates) {
                        if (other == anchor || other.isRemoved() || anchor.distanceToSqr(other) > radiusSqr) continue;
                        if (merge(anchor, other)) {
                            ++mergedEntities;
                            if (System.nanoTime() >= deadline) break;
                        }
                    }
                }
            }
        }
        state.cursor = start + Math.max(1, visited);
        PerformanceMetrics.add("items.merge.entities_checked", visited);
        PerformanceMetrics.add("items.merge.entities_removed", mergedEntities);
    }

    private static boolean merge(ItemEntity anchor, ItemEntity other) {
        if (!(anchor instanceof ManagedItemEntity left) || !(other instanceof ManagedItemEntity right)) return false;
        if (left.sporeperformance$isPlayerDropped() != right.sporeperformance$isPlayerDropped()
                || !Objects.equals(left.sporeperformance$getTarget(), right.sporeperformance$getTarget())
                || !Objects.equals(left.sporeperformance$getThrower(), right.sporeperformance$getThrower())) return false;
        ItemStack a = anchor.getItem();
        ItemStack b = other.getItem();
        if (!ItemStack.isSameItemSameTags(a, b) || !a.areCapsCompatible(b) || a.getCount() >= a.getMaxStackSize()) return false;
        int before = b.getCount();
        anchor.setItem(ItemEntity.merge(a, b, a.getMaxStackSize()));
        int moved = before - b.getCount();
        if (moved <= 0) return false;
        left.sporeperformance$setPickupDelay(Math.max(left.sporeperformance$getPickupDelay(), right.sporeperformance$getPickupDelay()));
        left.sporeperformance$setAge(Math.min(left.sporeperformance$getAge(), right.sporeperformance$getAge()));
        anchor.lifespan = Math.max(anchor.lifespan, other.lifespan);
        if (b.isEmpty()) other.discard();
        PerformanceMetrics.add("items.merge.stack_units", moved);
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(anchor.getItem().getItem());
        PerformanceMetrics.add("items.merge.units." + key, moved);
        return b.isEmpty();
    }

    public List<String> statusLines() {
        int levels = states.size();
        int items = states.values().stream().mapToInt(state -> state.items.size()).sum();
        Map<String, Integer> byId = new java.util.TreeMap<>();
        for (State state : states.values()) for (ItemEntity item : state.items) {
            String id = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem().getItem()));
            byId.merge(id, 1, Integer::sum);
        }
        String top = byId.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(8).map(entry -> entry.getKey() + "=" + entry.getValue()).collect(java.util.stream.Collectors.joining(", "));
        return List.of("Item coordinator: dimensions=" + levels + ", managed loaded items=" + items
                + ", Spore-only=" + !PerformanceConfig.ITEM_MERGE_GLOBAL.get(), "Managed item top: " + top);
    }

    public void clear() { states.clear(); }
    private State state(ServerLevel level) { return states.computeIfAbsent(level, ignored -> new State()); }
    private static long chunkKey(ItemEntity item) {
        return net.minecraft.world.level.ChunkPos.asLong(Mth.floor(item.getX()) >> 4, Mth.floor(item.getZ()) >> 4);
    }
    private static final class State {
        private final Set<ItemEntity> items = new LinkedHashSet<>();
        private int cursor;
    }
    private ItemMergeCoordinator() {}
}
