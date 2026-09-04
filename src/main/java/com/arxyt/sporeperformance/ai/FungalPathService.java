package com.arxyt.sporeperformance.ai;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import com.arxyt.sporeperformance.diagnostics.DebugTrace;
import com.arxyt.sporeperformance.diagnostics.CalamityTrace;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/** Shared native-path copies plus immutable-snapshot asynchronous coarse corridors. */
public final class FungalPathService implements AutoCloseable {
    private static final int MAX_GRID = 64;
    private static final int MAX_CACHED_PATH_RESUME_SCAN = 8;
    private final ServerLevel level;
    private final ExecutorService workers;
    private final Queue<CorridorRequest> snapshots = new ArrayDeque<>();
    private final Queue<CorridorResult> completed = new ConcurrentLinkedQueue<>();
    private final Set<CorridorKey> inFlight = new HashSet<>();
    private final LinkedHashMap<CorridorKey, CachedCorridor> corridors = new LinkedHashMap<>(128, 0.75F, true);
    private final LinkedHashMap<NativeKey, CachedPath> nativePaths = new LinkedHashMap<>(256, 0.75F, true);
    private final LinkedHashMap<PositionKey, CachedPath> positionPaths = new LinkedHashMap<>(128, 0.75F, true);
    /** Monotonic diagnostic counter; unlike the old implementation it is not a cache key. */
    private long terrainVersion;
    /** Only the altered 16x16x16 section changes its version. */
    private final Map<Long, Long> sectionVersions = new HashMap<>();

    FungalPathService(ServerLevel level) {
        this.level = level;
        int count = PerformanceConfig.REFACTOR_PATH_WORKERS.get();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "SporePerformance-Path-" + level.dimension().location());
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        };
        workers = Executors.newFixedThreadPool(count, factory);
    }

    public boolean enabledFor(Mob mob) {
        return PerformanceConfig.REFACTOR_AI_ENABLED.get() && PerformanceConfig.REFACTOR_NAVIGATION_ENABLED.get()
                && FungalAiRuntime.isSpore(mob);
    }

    @Nullable
    public Path cachedNativePath(Mob mob, Entity target) {
        if (!enabledFor(mob) || !PerformanceConfig.REFACTOR_SHARED_CORRIDORS.get()) return null;
        NativeKey key = NativeKey.of(mob, target);
        CachedPath cached = nativePaths.get(key);
        if (cached == null || cached.expiresAt < level.getGameTime() || !routeCurrent(cached.route)) {
            nativePaths.remove(key);
            CalamityTrace.internalPath(mob, "native_cache_miss", "target=" + target.getUUID() + ",reason="
                    + cacheMissReason(cached));
            return null;
        }
        PerformanceMetrics.increment("ai_refactor.path.native_cache_hits");
        CalamityTrace.internalPath(mob, "native_cache_hit", "target=" + target.getUUID() + ",nodes=" + cached.path.getNodeCount()
                + ",sections=" + cached.route.sections.size());
        if (DebugTrace.enabled(DebugTrace.Category.NAVIGATION))
            DebugTrace.event(DebugTrace.Category.NAVIGATION, level, DebugTrace.trace(mob), mob,
                    "native_cache_hit", "target=" + target.getUUID() + ",nodes=" + cached.path.getNodeCount());
        return copyAtCurrentProgress(cached.path, mob);
    }

    public void recordNativePath(Mob mob, Entity target, @Nullable Path path) {
        if (path == null) {
            CalamityTrace.internalPath(mob, "native_result", "target=" + target.getUUID() + ",success=false,terrainVersion=" + terrainVersion);
            return;
        }
        if (!path.canReach()) {
            nativePaths.remove(NativeKey.of(mob, target));
            CalamityTrace.internalPath(mob, "native_result", "target=" + target.getUUID()
                    + ",success=partial,stored=false,nodes=" + path.getNodeCount());
            PerformanceMetrics.increment("ai_refactor.path.partial_not_cached");
            return;
        }
        if (!enabledFor(mob) || !PerformanceConfig.REFACTOR_SHARED_CORRIDORS.get()) return;
        nativePaths.put(NativeKey.of(mob, target), new CachedPath(copy(path),
                level.getGameTime() + PerformanceConfig.REFACTOR_PATH_CACHE_TICKS.get(),
                routeStamp(path, mob.blockPosition(), target.blockPosition())));
        trim(nativePaths, PerformanceConfig.REFACTOR_PATH_CACHE_ENTRIES.get());
        PerformanceMetrics.increment("ai_refactor.path.native_cache_stores");
        CalamityTrace.internalPath(mob, "native_result", "target=" + target.getUUID() + ",success=true,nodes=" + path.getNodeCount()
                + ",stored=true,sections=" + routeStamp(path, mob.blockPosition(), target.blockPosition()).sections.size());
        if (DebugTrace.enabled(DebugTrace.Category.NAVIGATION))
            DebugTrace.event(DebugTrace.Category.NAVIGATION, level, DebugTrace.trace(mob), mob,
                    "native_cache_store", "target=" + target.getUUID() + ",nodes=" + path.getNodeCount());
    }

    @Nullable
    public Path cachedNativePath(Mob mob, BlockPos target) {
        if (!enabledFor(mob) || !PerformanceConfig.REFACTOR_CALAMITY_POSITION_PATH_CACHE.get()) return null;
        PositionKey key = PositionKey.of(mob, target);
        CachedPath cached = positionPaths.get(key);
        if (cached == null || cached.expiresAt < level.getGameTime() || !routeCurrent(cached.route)) {
            positionPaths.remove(key);
            CalamityTrace.internalPath(mob, "position_cache_miss", "target=" + target + ",reason=" + cacheMissReason(cached));
            return null;
        }
        PerformanceMetrics.increment("ai_refactor.path.position_cache_hits");
        CalamityTrace.internalPath(mob, "position_cache_hit", "target=" + target + ",nodes=" + cached.path.getNodeCount()
                + ",sections=" + cached.route.sections.size());
        return copyAtCurrentProgress(cached.path, mob);
    }

    public void recordNativePath(Mob mob, BlockPos target, @Nullable Path path) {
        if (path == null) {
            CalamityTrace.internalPath(mob, "position_result", "target=" + target + ",success=false");
            return;
        }
        if (!path.canReach()) {
            positionPaths.remove(PositionKey.of(mob, target));
            CalamityTrace.internalPath(mob, "position_result", "target=" + target
                    + ",success=partial,stored=false,nodes=" + path.getNodeCount());
            PerformanceMetrics.increment("ai_refactor.path.partial_not_cached");
            return;
        }
        if (!enabledFor(mob) || !PerformanceConfig.REFACTOR_CALAMITY_POSITION_PATH_CACHE.get()) return;
        RouteStamp stamp = routeStamp(path, mob.blockPosition(), target);
        positionPaths.put(PositionKey.of(mob, target), new CachedPath(copy(path),
                level.getGameTime() + PerformanceConfig.REFACTOR_PATH_CACHE_TICKS.get(), stamp));
        trim(positionPaths, PerformanceConfig.REFACTOR_PATH_CACHE_ENTRIES.get());
        PerformanceMetrics.increment("ai_refactor.path.position_cache_stores");
        CalamityTrace.internalPath(mob, "position_result", "target=" + target + ",success=true,nodes=" + path.getNodeCount()
                + ",sections=" + stamp.sections.size());
    }

    @Nullable
    public BlockPos corridorWaypoint(Mob mob, Entity target) {
        if (!enabledFor(mob) || !PerformanceConfig.REFACTOR_ASYNC_LONG_PATHS.get()
                || mob.distanceToSqr(target) < Mth.square(PerformanceConfig.REFACTOR_ASYNC_THRESHOLD.get())
                || !supportsAsync(mob.getNavigation())) return null;
        CorridorKey key = CorridorKey.of(mob, target);
        CachedCorridor cached = corridors.get(key);
        long now = level.getGameTime();
        if (cached != null && cached.expiresAt >= now && routeCurrent(cached.route) && !cached.points.isEmpty()) {
            BlockPos waypoint = nextWaypoint(mob.blockPosition(), cached.points);
            if (waypoint != null) {
                PerformanceMetrics.increment("ai_refactor.path.corridor_hits");
                if (DebugTrace.enabled(DebugTrace.Category.NAVIGATION))
                    DebugTrace.event(DebugTrace.Category.NAVIGATION, level, DebugTrace.trace(mob), mob,
                            "corridor_hit", "target=" + target.getUUID() + ",waypoint=" + waypoint);
                return waypoint;
            }
        }
        if (!inFlight.contains(key) && snapshots.size() < PerformanceConfig.REFACTOR_PATH_QUEUE_LIMIT.get()) {
            snapshots.add(new CorridorRequest(key, mob.blockPosition(), target.blockPosition(), routeStamp(null, mob.blockPosition(), target.blockPosition())));
            inFlight.add(key);
            PerformanceMetrics.increment("ai_refactor.path.requests_queued");
            if (DebugTrace.enabled(DebugTrace.Category.NAVIGATION))
                DebugTrace.event(DebugTrace.Category.NAVIGATION, level, DebugTrace.trace(mob), mob,
                        "corridor_queued", "target=" + target.getUUID() + ",queue=" + snapshots.size());
        } else if (inFlight.contains(key)) {
            PerformanceMetrics.increment("ai_refactor.path.requests_coalesced");
            if (DebugTrace.enabled(DebugTrace.Category.NAVIGATION))
                DebugTrace.event(DebugTrace.Category.NAVIGATION, level, DebugTrace.trace(mob), mob,
                        "corridor_coalesced", "target=" + target.getUUID());
        } else {
            PerformanceMetrics.increment("ai_refactor.path.queue_rejected");
            if (DebugTrace.enabled(DebugTrace.Category.NAVIGATION))
                DebugTrace.event(DebugTrace.Category.NAVIGATION, level, DebugTrace.trace(mob), mob,
                        "corridor_rejected", "target=" + target.getUUID() + ",queue=" + snapshots.size());
        }
        return null;
    }

    public void tick() {
        acceptResults();
        int budget = PerformanceConfig.REFACTOR_PATH_SNAPSHOT_BUDGET.get();
        while (budget-- > 0) {
            CorridorRequest request = snapshots.poll();
            if (request == null) break;
            GridPathfinder.Grid snapshot = snapshot(request);
            if (snapshot == null) {
                inFlight.remove(request.key);
                if (DebugTrace.enabled(DebugTrace.Category.NAVIGATION))
                    DebugTrace.event(DebugTrace.Category.NAVIGATION, level, 0L, null,
                            "snapshot_rejected", "key=" + request.key);
                continue;
            }
            workers.execute(() -> {
                try {
                    completed.add(new CorridorResult(request.key, request.start, request.goal, request.requestStamp,
                            GridPathfinder.find(snapshot), null));
                } catch (Throwable throwable) {
                    completed.add(new CorridorResult(request.key, request.start, request.goal, request.requestStamp, List.of(),
                            throwable.getClass().getName() + ":" + String.valueOf(throwable.getMessage())));
                }
            });
            PerformanceMetrics.increment("ai_refactor.path.snapshots_created");
            if (DebugTrace.enabled(DebugTrace.Category.NAVIGATION))
                DebugTrace.event(DebugTrace.Category.NAVIGATION, level, 0L, null,
                        "snapshot_submitted", "key=" + request.key + ",queue=" + snapshots.size());
        }
        long now = level.getGameTime();
        corridors.entrySet().removeIf(entry -> entry.getValue().expiresAt < now || !routeCurrent(entry.getValue().route));
        nativePaths.entrySet().removeIf(entry -> entry.getValue().expiresAt < now || !routeCurrent(entry.getValue().route));
        positionPaths.entrySet().removeIf(entry -> entry.getValue().expiresAt < now || !routeCurrent(entry.getValue().route));
    }

    public void markTerrainChanged(BlockPos changed) {
        AirSweepContext.invalidate(level);
        terrainVersion++;
        if (terrainVersion == Long.MAX_VALUE) terrainVersion = 1L;
        if (PerformanceConfig.REFACTOR_CALAMITY_SECTION_PATH_INVALIDATION.get()) {
            long section = SectionPos.asLong(changed.getX() >> 4, changed.getY() >> 4, changed.getZ() >> 4);
            long version = sectionVersions.getOrDefault(section, 0L) + 1L;
            sectionVersions.put(section, version == Long.MAX_VALUE ? 1L : version);
        } else {
            // Disabling the feature explicitly restores conservative whole-level invalidation.
            corridors.clear();
            nativePaths.clear();
            positionPaths.clear();
        }
        if (DebugTrace.enabled(DebugTrace.Category.NAVIGATION))
            DebugTrace.event(DebugTrace.Category.NAVIGATION, level, 0L, null,
                    "terrain_invalidated", "pos=" + changed + ",version=" + terrainVersion);
    }

    public int queued() { return snapshots.size(); }
    public int inFlight() { return inFlight.size(); }
    public int corridorCount() { return corridors.size(); }
    public int nativeCacheCount() { return nativePaths.size() + positionPaths.size(); }
    public long terrainVersion() { return terrainVersion; }
    ServerLevel level() { return level; }

    private void acceptResults() {
        int budget = PerformanceConfig.REFACTOR_PATH_RESULT_BUDGET.get();
        long expires = level.getGameTime() + PerformanceConfig.REFACTOR_PATH_CACHE_TICKS.get();
        while (budget-- > 0) {
            CorridorResult result = completed.poll();
            if (result == null) break;
            inFlight.remove(result.key);
            if (result.failure != null) {
                if (DebugTrace.enabled(DebugTrace.Category.FAULT))
                    DebugTrace.event(DebugTrace.Category.FAULT, level, 0L, null,
                            "async_path_failed", "key=" + result.key + ",error=" + result.failure);
                continue;
            }
            if (!routeCurrent(result.requestStamp) || result.cells.isEmpty()) {
                if (DebugTrace.enabled(DebugTrace.Category.NAVIGATION))
                    DebugTrace.event(DebugTrace.Category.NAVIGATION, level, 0L, null,
                            "result_discarded", "key=" + result.key + ",cells=" + result.cells.size());
                continue;
            }
            List<BlockPos> positions = result.cells.stream().map(cell -> new BlockPos(cell.x(), cell.y(), cell.z())).toList();
            corridors.put(result.key, new CachedCorridor(positions, expires,
                    routeStamp(null, result.start, result.goal, positions)));
            trim(corridors, PerformanceConfig.REFACTOR_PATH_CACHE_ENTRIES.get());
            PerformanceMetrics.increment("ai_refactor.path.results_applied");
            if (DebugTrace.enabled(DebugTrace.Category.NAVIGATION))
                DebugTrace.event(DebugTrace.Category.NAVIGATION, level, 0L, null,
                        "result_applied", "key=" + result.key + ",cells=" + result.cells.size());
        }
    }

    @Nullable
    private GridPathfinder.Grid snapshot(CorridorRequest request) {
        BlockPos start = request.start;
        BlockPos desired = request.goal;
        double dx = desired.getX() - start.getX();
        double dz = desired.getZ() - start.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0D) return null;
        double scale = Math.min(1.0D, 48.0D / length);
        int goalX = start.getX() + Mth.floor(dx * scale);
        int goalZ = start.getZ() + Mth.floor(dz * scale);
        int minX = Math.min(start.getX(), goalX) - 8;
        int minZ = Math.min(start.getZ(), goalZ) - 8;
        int width = Math.min(MAX_GRID, Math.abs(goalX - start.getX()) + 17);
        int depth = Math.min(MAX_GRID, Math.abs(goalZ - start.getZ()) + 17);
        if (width <= 0 || depth <= 0) return null;
        boolean[] passable = new boolean[width * depth];
        int[] heights = new int[width * depth];
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int z = 0; z < depth; z++) {
            for (int x = 0; x < width; x++) {
                int worldX = minX + x;
                int worldZ = minZ + z;
                if (!level.hasChunkAt(cursor.set(worldX, start.getY(), worldZ))) continue;
                int foundY = Integer.MIN_VALUE;
                for (int y = start.getY() + 3; y >= start.getY() - 3; y--) {
                    cursor.set(worldX, y, worldZ);
                    BlockState feet = level.getBlockState(cursor);
                    BlockState head = level.getBlockState(cursor.above());
                    BlockState floor = level.getBlockState(cursor.below());
                    if (feet.getCollisionShape(level, cursor).isEmpty()
                            && head.getCollisionShape(level, cursor.above()).isEmpty()
                            && !floor.getCollisionShape(level, cursor.below()).isEmpty()) { foundY = y; break; }
                }
                int index = z * width + x;
                if (foundY != Integer.MIN_VALUE) { passable[index] = true; heights[index] = foundY; }
            }
        }
        int sx = Mth.clamp(start.getX() - minX, 0, width - 1);
        int sz = Mth.clamp(start.getZ() - minZ, 0, depth - 1);
        int gx = Mth.clamp(goalX - minX, 0, width - 1);
        int gz = Mth.clamp(goalZ - minZ, 0, depth - 1);
        passable[sz * width + sx] = true;
        heights[sz * width + sx] = start.getY();
        return new GridPathfinder.Grid(minX, minZ, width, depth, sx, sz, gx, gz, passable, heights);
    }

    private static boolean supportsAsync(PathNavigation navigation) {
        String name = navigation.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        return !name.contains("fly") && !name.contains("swim") && !name.contains("water")
                && !name.contains("climb") && !name.contains("hybrid") && !name.contains("calamity")
                && !name.contains("underground");
    }

    @Nullable
    private static BlockPos nextWaypoint(BlockPos current, List<BlockPos> points) {
        BlockPos best = null;
        for (BlockPos point : points) {
            if (point.distSqr(current) <= 4.0D) continue;
            best = point;
            if (point.distSqr(current) >= 100.0D) break;
        }
        return best;
    }

    private static Path copy(Path source) {
        List<Node> nodes = new ArrayList<>(source.getNodeCount());
        for (int i = 0; i < source.getNodeCount(); i++) {
            Node original = source.getNode(i);
            Node node = new Node(original.x, original.y, original.z);
            node.type = original.type;
            node.costMalus = original.costMalus;
            nodes.add(node);
        }
        Path copy = new Path(nodes, source.getTarget(), source.canReach());
        copy.setNextNodeIndex(Math.min(source.getNextNodeIndex(), nodes.size()));
        return copy;
    }

    /**
     * A cached Path belongs to an earlier position inside the same four-block cache cell. Copying
     * its original node index on every move request makes a moving mob turn back toward nodes it
     * has already passed. Resume at the nearest node in a bounded forward window instead.
     */
    private static Path copyAtCurrentProgress(Path source, Mob mob) {
        Path copy = copy(source);
        copy.setNextNodeIndex(resumeIndex(copy, mob.getX(), mob.getY(), mob.getZ()));
        return copy;
    }

    static int resumeIndex(Path path, double x, double y, double z) {
        int count = path.getNodeCount();
        if (count == 0) return 0;
        int start = Mth.clamp(path.getNextNodeIndex(), 0, count - 1);
        int limit = Math.min(count, start + MAX_CACHED_PATH_RESUME_SCAN);
        int best = start;
        double bestDistance = nodeDistanceSqr(path.getNode(start), x, y, z);
        for (int index = start + 1; index < limit; ++index) {
            double distance = nodeDistanceSqr(path.getNode(index), x, y, z);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }
        return best;
    }

    private static double nodeDistanceSqr(Node node, double x, double y, double z) {
        double dx = node.x + 0.5D - x;
        double dy = node.y - y;
        double dz = node.z + 0.5D - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static <K, V> void trim(LinkedHashMap<K, V> map, int max) {
        while (map.size() > max) map.remove(map.keySet().iterator().next());
    }

    private RouteStamp routeStamp(@Nullable Path path, BlockPos... anchors) {
        Map<Long, Long> sections = new HashMap<>();
        for (BlockPos anchor : anchors) addSection(sections, anchor);
        if (path != null) {
            for (int index = 0; index < path.getNodeCount(); ++index) {
                Node node = path.getNode(index);
                addSection(sections, new BlockPos(node.x, node.y, node.z));
            }
            addSection(sections, path.getTarget());
        }
        return new RouteStamp(Map.copyOf(sections), terrainVersion);
    }

    private RouteStamp routeStamp(@Nullable Path path, BlockPos start, BlockPos goal, List<BlockPos> points) {
        Map<Long, Long> sections = new HashMap<>();
        addSection(sections, start);
        addSection(sections, goal);
        for (BlockPos point : points) addSection(sections, point);
        if (path != null) {
            for (int index = 0; index < path.getNodeCount(); ++index) {
                Node node = path.getNode(index);
                addSection(sections, new BlockPos(node.x, node.y, node.z));
            }
            addSection(sections, path.getTarget());
        }
        return new RouteStamp(Map.copyOf(sections), terrainVersion);
    }

    private void addSection(Map<Long, Long> sections, BlockPos position) {
        long section = SectionPos.asLong(position.getX() >> 4, position.getY() >> 4, position.getZ() >> 4);
        sections.putIfAbsent(section, sectionVersions.getOrDefault(section, 0L));
    }

    private boolean routeCurrent(RouteStamp stamp) {
        if (!PerformanceConfig.REFACTOR_CALAMITY_SECTION_PATH_INVALIDATION.get()) {
            return stamp.fallbackTerrainVersion == terrainVersion;
        }
        for (Map.Entry<Long, Long> entry : stamp.sections.entrySet()) {
            if (sectionVersions.getOrDefault(entry.getKey(), 0L).longValue() != entry.getValue()) return false;
        }
        return true;
    }

    private String cacheMissReason(@Nullable CachedPath cached) {
        if (cached == null) return "absent";
        if (cached.expiresAt < level.getGameTime()) return "expired";
        return "section_changed";
    }

    @Override public void close() {
        workers.shutdownNow();
        snapshots.clear(); completed.clear(); inFlight.clear(); corridors.clear(); nativePaths.clear(); positionPaths.clear(); sectionVersions.clear();
    }

    /* Native Path nodes contain an entity-specific access segment. Keep those paths per mob;
       cross-entity reuse happens at the coarse CorridorKey layer, where every mob builds its
       own validated local path to the next waypoint. */
    private record NativeKey(int mobId, String navigation, int size, int sx, int sy, int sz,
                             int tx, int ty, int tz) {
        static NativeKey of(Mob mob, Entity target) {
            int size = Mth.ceil(Math.max(mob.getBbWidth(), mob.getBbHeight()) * 2.0F);
            double threshold = PerformanceConfig.REFACTOR_PATH_TARGET_MOVE_INVALIDATION.get();
            return new NativeKey(mob.getId(), mob.getNavigation().getClass().getName(), size,
                    Mth.floor(mob.getX()) >> 2, Mth.floor(mob.getY()) >> 2, Mth.floor(mob.getZ()) >> 2,
                    Mth.floor(target.getX() / threshold), Mth.floor(target.getY() / threshold),
                    Mth.floor(target.getZ() / threshold));
        }
    }
    private record PositionKey(int mobId, String navigation, int size, int sx, int sy, int sz, int tx, int ty, int tz) {
        static PositionKey of(Mob mob, BlockPos target) {
            int size = Mth.ceil(Math.max(mob.getBbWidth(), mob.getBbHeight()) * 2.0F);
            return new PositionKey(mob.getId(), mob.getNavigation().getClass().getName(), size,
                    Mth.floor(mob.getX()) >> 2, Mth.floor(mob.getY()) >> 2, Mth.floor(mob.getZ()) >> 2,
                    target.getX(), target.getY(), target.getZ());
        }
    }
    private record CorridorKey(String navigation, int size, int sx, int sy, int sz, int tx, int ty, int tz) {
        static CorridorKey of(Mob mob, Entity target) {
            int size = Mth.ceil(Math.max(mob.getBbWidth(), mob.getBbHeight()) * 2.0F);
            double threshold = PerformanceConfig.REFACTOR_PATH_TARGET_MOVE_INVALIDATION.get();
            return new CorridorKey(mob.getNavigation().getClass().getName(), size,
                    Mth.floor(mob.getX()) >> 4, Mth.floor(mob.getY()) >> 4, Mth.floor(mob.getZ()) >> 4,
                    Mth.floor(target.getX() / threshold), Mth.floor(target.getY() / threshold),
                    Mth.floor(target.getZ() / threshold));
        }
    }
    private record RouteStamp(Map<Long, Long> sections, long fallbackTerrainVersion) {}
    private record CachedPath(Path path, long expiresAt, RouteStamp route) {}
    private record CachedCorridor(List<BlockPos> points, long expiresAt, RouteStamp route) {}
    private record CorridorRequest(CorridorKey key, BlockPos start, BlockPos goal, RouteStamp requestStamp) {}
    private record CorridorResult(CorridorKey key, BlockPos start, BlockPos goal, RouteStamp requestStamp,
                                  List<GridPathfinder.Cell> cells, String failure) {}
}
