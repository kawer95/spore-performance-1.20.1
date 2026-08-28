package com.arxyt.sporeperformance.diagnostics;

import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dedicated Calamity diagnostics emitted from the add-on's own AI runtime and path service.
 * It intentionally has no controller or vanilla navigation Mixin: the trace describes the
 * decisions our refactor made, not a second observation pipeline around the original AI.
 */
public final class CalamityTrace {
    public static final CalamityTrace INSTANCE = new CalamityTrace();
    private static final int QUEUE_CAPACITY = 4096;
    private static final long PROXIMITY_REFRESH_TICKS = 10L;
    private static final long STALE_STATE_TICKS = 40L;

    private final Map<UUID, TraceState> states = new HashMap<>();
    private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
    private final AtomicLong dropped = new AtomicLong();
    private final String session = UUID.randomUUID().toString();
    private volatile java.nio.file.Path file;
    private volatile Thread writer;
    private volatile boolean closed;
    private long rateSecond = Long.MIN_VALUE;
    private int emittedThisSecond;

    private CalamityTrace() {}

    /** Called by FungalAiRuntime after it has updated the shared spatial index for this entity. */
    public void recordRuntimeTick(Calamity calamity, ServerLevel level, FungalAiRuntime.LevelRuntime runtime) {
        if (!accepts(calamity, level)) return;
        long tick = level.getGameTime();
        TraceState state = states.get(calamity.getUUID());
        if (state == null || !sampled(calamity, tick)) return;

        String decision = decisionSignature(calamity);
        if (!decision.equals(state.lastDecision)) {
            emit(level, calamity, "runtime_decision_changed", "before=" + state.lastDecision + ",after=" + decision);
            state.lastDecision = decision;
        }
        emit(level, calamity, "runtime_snapshot", snapshot(calamity, runtime));
    }

    /** Called only from FungalPathService's cache, queue and result transitions. */
    public static void internalPath(Mob mob, String event, String detail) {
        if (mob instanceof Calamity calamity) INSTANCE.recordInternalPath(calamity, event, detail);
    }

    /**
     * Emits an internal decision made by CalamityNavigationRuntime.  Keeping this entry point in
     * the trace service ensures the runtime remains the only producer; we deliberately do not
     * add controller or vanilla-navigation observation mixins just for diagnostics.
     */
    public void navigationRuntime(Calamity calamity, String event, String detail) {
        if (!(calamity.level() instanceof ServerLevel level) || !accepts(calamity, level)) return;
        emit(level, calamity, "navigation_" + event, detail);
    }

    public void clearLevel(ServerLevel level) {
        String dimension = level.dimension().location().toString();
        states.entrySet().removeIf(entry -> dimension.equals(entry.getValue().dimension));
    }

    public List<String> statusLines() {
        List<String> result = new ArrayList<>();
        result.add("Calamity trace: enabled=" + PerformanceConfig.CALAMITY_TRACE_ENABLED.get()
                + ", file=" + file() + ", queued=" + queue.size() + ", dropped=" + dropped.get());
        result.add("Calamity trace: tracked=" + activeStates() + "/" + PerformanceConfig.CALAMITY_TRACE_MAX_TRACKED.get()
                + ", radius=" + PerformanceConfig.CALAMITY_TRACE_RADIUS.get()
                + ", sampleTicks=" + PerformanceConfig.CALAMITY_TRACE_SAMPLE_INTERVAL.get());
        return result;
    }

    public void reset() {
        states.clear();
        dropped.set(0L);
        synchronized (this) {
            rateSecond = Long.MIN_VALUE;
            emittedThisSecond = 0;
        }
    }

    public void close() {
        states.clear();
        closed = true;
        Thread current = writer;
        if (current != null) current.interrupt();
        writer = null;
        queue.clear();
    }

    public java.nio.file.Path file() {
        java.nio.file.Path current = file;
        return current != null ? current : FMLPaths.GAMEDIR.get().resolve("logs").resolve("spore-performance-calamity-trace.jsonl");
    }

    private void recordInternalPath(Calamity calamity, String event, String detail) {
        if (!(calamity.level() instanceof ServerLevel level) || !accepts(calamity, level)) return;
        emit(level, calamity, "path_" + event, detail);
    }

    private boolean accepts(Calamity calamity, ServerLevel level) {
        if (!PerformanceConfig.CALAMITY_TRACE_ENABLED.get()) return false;
        long now = level.getGameTime();
        TraceState state = states.computeIfAbsent(calamity.getUUID(), ignored -> new TraceState());
        state.dimension = level.dimension().location().toString();
        if (state.lastProximityCheck == Long.MIN_VALUE || now - state.lastProximityCheck >= PROXIMITY_REFRESH_TICKS) {
            state.lastProximityCheck = now;
            boolean nextActive = level.getNearestPlayer(calamity, PerformanceConfig.CALAMITY_TRACE_RADIUS.get()) != null;
            if (nextActive && !state.active && activeStates(now) >= PerformanceConfig.CALAMITY_TRACE_MAX_TRACKED.get()) nextActive = false;
            if (state.active && !nextActive) emit(level, calamity, "left_trace_radius", "radius=" + PerformanceConfig.CALAMITY_TRACE_RADIUS.get());
            if (!state.active && nextActive) emit(level, calamity, "entered_trace_radius", "radius=" + PerformanceConfig.CALAMITY_TRACE_RADIUS.get());
            state.active = nextActive;
        }
        if (state.active) state.lastSeen = now;
        return state.active;
    }

    private int activeStates() {
        long newest = 0L;
        for (TraceState state : states.values()) newest = Math.max(newest, state.lastSeen);
        return activeStates(newest);
    }

    private int activeStates(long now) {
        int active = 0;
        Iterator<Map.Entry<UUID, TraceState>> iterator = states.entrySet().iterator();
        while (iterator.hasNext()) {
            TraceState state = iterator.next().getValue();
            if (state.lastSeen != Long.MIN_VALUE && now - state.lastSeen > STALE_STATE_TICKS) {
                iterator.remove();
            } else if (state.active) {
                ++active;
            }
        }
        return active;
    }

    private boolean sampled(Calamity calamity, long tick) {
        int interval = PerformanceConfig.CALAMITY_TRACE_SAMPLE_INTERVAL.get();
        return interval <= 1 || Math.floorMod(tick + calamity.getId(), interval) == 0;
    }

    private String decisionSignature(Calamity calamity) {
        List<String> goals = new ArrayList<>();
        for (WrappedGoal wrapped : calamity.goalSelector.getAvailableGoals()) {
            if (wrapped.isRunning()) goals.add(wrapped.getPriority() + ":" + wrapped.getGoal().getClass().getSimpleName());
        }
        goals.sort(String::compareTo);
        LivingEntity target = calamity.getTarget();
        String targetText = target == null ? "none" : entityId(target) + '#' + target.getId();
        return "goals=" + String.join("|", goals) + ",target=" + targetText + ",search=" + block(calamity.getSearchArea())
                + "," + navigationSummary(calamity.getNavigation());
    }

    private String snapshot(Calamity calamity, FungalAiRuntime.LevelRuntime runtime) {
        String result = decisionSignature(calamity)
                + ",runtime{indexed=" + runtime.index.size() + ",pathQueue=" + runtime.paths.queued()
                + ",pathInFlight=" + runtime.paths.inFlight() + ",corridors=" + runtime.paths.corridorCount()
                + ",nativePaths=" + runtime.paths.nativeCacheCount() + ",terrainVersion=" + runtime.paths.terrainVersion()
                + "," + runtime.calamities.status() + '}';
        if (!PerformanceConfig.CALAMITY_TRACE_INCLUDE_COORDINATES.get()) return result;
        return result + ",pos=" + vector(calamity.getX(), calamity.getY(), calamity.getZ())
                + ",velocity=" + vector(calamity.getDeltaMovement().x, calamity.getDeltaMovement().y, calamity.getDeltaMovement().z)
                + ",yaw=" + round(calamity.getYRot()) + ",headYaw=" + round(calamity.yHeadRot)
                + ",bodyYaw=" + round(calamity.yBodyRot) + ",pitch=" + round(calamity.getXRot());
    }

    private String navigationSummary(PathNavigation navigation) {
        Path path = navigation.getPath();
        return "navDone=" + navigation.isDone() + ",navTarget=" + block(navigation.getTargetPos()) + "," + pathSummary(path);
    }

    private String pathSummary(Path path) {
        if (path == null) return "path=null";
        int index = path.getNextNodeIndex();
        String node = "none";
        if (index >= 0 && index < path.getNodeCount()) {
            Node value = path.getNode(index);
            node = value.x + "," + value.y + "," + value.z;
        }
        return "pathNodes=" + path.getNodeCount() + ",pathNext=" + index + ",pathNextPos=" + node
                + ",pathDone=" + path.isDone();
    }

    private void emit(ServerLevel level, Calamity calamity, String event, String detail) {
        if (!allowEvent()) return;
        StringBuilder json = new StringBuilder(256);
        json.append('{')
                .append("\"time\":\"").append(Instant.now()).append("\",")
                .append("\"session\":\"").append(session).append("\",")
                .append("\"tick\":").append(level.getGameTime()).append(',')
                .append("\"event\":\"").append(escape(event)).append("\",")
                .append("\"dimension\":\"").append(escape(level.dimension().location().toString())).append("\",")
                .append("\"entityId\":").append(calamity.getId()).append(',')
                .append("\"uuid\":\"").append(calamity.getUUID()).append("\",")
                .append("\"entityType\":\"").append(escape(entityId(calamity))).append("\",")
                .append("\"detail\":\"").append(escape(detail)).append("\"}");
        if (!queue.offer(json.toString())) dropped.incrementAndGet();
        else ensureWriter();
    }

    private synchronized boolean allowEvent() {
        long second = System.currentTimeMillis() / 1000L;
        if (second != rateSecond) {
            rateSecond = second;
            emittedThisSecond = 0;
        }
        if (emittedThisSecond >= PerformanceConfig.CALAMITY_TRACE_MAX_EVENTS_PER_SECOND.get()) {
            dropped.incrementAndGet();
            return false;
        }
        ++emittedThisSecond;
        return true;
    }

    private synchronized void ensureWriter() {
        if (writer != null && writer.isAlive()) return;
        closed = false;
        file = FMLPaths.GAMEDIR.get().resolve("logs").resolve("spore-performance-calamity-trace.jsonl");
        writer = new Thread(this::writeLoop, "SporePerformance-CalamityTrace");
        writer.setDaemon(true);
        writer.setPriority(Thread.MIN_PRIORITY);
        writer.start();
    }

    private void writeLoop() {
        java.nio.file.Path output = file();
        try {
            Files.createDirectories(output.getParent());
            try (BufferedWriter stream = Files.newBufferedWriter(output, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
                while (!closed || !queue.isEmpty()) {
                    String line = queue.poll();
                    if (line == null) {
                        try { Thread.sleep(25L); }
                        catch (InterruptedException ignored) { /* Recheck close state. */ }
                        continue;
                    }
                    stream.write(line);
                    stream.newLine();
                }
            }
        } catch (IOException exception) {
            SporePerformance.LOGGER.warn("Unable to write Calamity trace {}: {}", output, exception.toString());
            dropped.addAndGet(queue.size());
            queue.clear();
        }
    }

    private static String entityId(Entity entity) {
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return key == null ? entity.getType().toString() : key.toString();
    }

    private static String block(BlockPos pos) {
        return pos == null ? "none" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String vector(double x, double y, double z) {
        return round(x) + "," + round(y) + "," + round(z);
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static final class TraceState {
        private String dimension = "";
        private long lastProximityCheck = Long.MIN_VALUE;
        private long lastSeen = Long.MIN_VALUE;
        private boolean active;
        private String lastDecision = "initial";
    }
}
