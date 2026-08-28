package com.arxyt.sporeperformance.diagnostics;

import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded structured diagnostics. Disabled calls return before allocating event data. */
public final class DebugTrace {
    public enum Category { LIFECYCLE, PERCEPTION, THREAT, GROUP, NAVIGATION, GOAL, COMBAT, STAHL, BACKGROUND, COMPAT, FAULT }

    private static final LinkedBlockingQueue<String> WRITE_QUEUE = new LinkedBlockingQueue<>(8192);
    private static final Deque<String> RECENT = new ArrayDeque<>();
    private static final Set<UUID> WATCHED = ConcurrentHashMap.newKeySet();
    private static final AtomicLong TRACE_SEQUENCE = new AtomicLong();
    private static final AtomicLong DROPPED = new AtomicLong();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static volatile Thread writerThread;
    private static volatile long rateSecond;
    private static volatile int rateCount;
    private static volatile String session = UUID.randomUUID().toString();
    private static final Path FILE = FMLPaths.GAMEDIR.get().resolve("logs").resolve("spore-performance-debug.jsonl");

    public static boolean enabled(Category category) {
        if (!PerformanceConfig.DEBUG_ENABLED.get()) return false;
        return switch (category) {
            case PERCEPTION -> PerformanceConfig.DEBUG_PERCEPTION.get();
            case THREAT, GROUP -> PerformanceConfig.DEBUG_GROUPS.get();
            case NAVIGATION -> PerformanceConfig.DEBUG_NAVIGATION.get();
            case GOAL -> PerformanceConfig.DEBUG_GOALS.get();
            case COMBAT -> PerformanceConfig.DEBUG_COMBAT.get();
            case STAHL -> PerformanceConfig.DEBUG_STAHL.get();
            case BACKGROUND -> PerformanceConfig.DEBUG_BACKGROUND.get();
            case COMPAT -> PerformanceConfig.DEBUG_COMPAT.get();
            case LIFECYCLE -> PerformanceConfig.DEBUG_LIFECYCLE.get();
            case FAULT -> true;
        };
    }

    public static long trace(Entity entity) {
        long sequence = TRACE_SEQUENCE.incrementAndGet() & 0xFFFFFL;
        return ((long) entity.getId() << 32) ^ sequence;
    }

    public static void event(Category category, ServerLevel level, long trace, Entity entity, String action, String detail) {
        emit(category, level, trace, entity, action, detail, false, false);
    }

    /**
     * Low-frequency health/state transitions must remain visible even while a dense combat scene
     * consumes the ordinary trace budget.  They still use the same bounded queue, so they never
     * make the server thread wait for disk I/O.
     */
    public static void state(Category category, ServerLevel level, long trace, Entity entity, String action, String detail) {
        emit(category, level, trace, entity, action, detail, false, true);
    }

    public static void fault(ServerLevel level, long trace, Entity entity, String action, Throwable throwable) {
        String detail = throwable.getClass().getName() + ":" + String.valueOf(throwable.getMessage());
        emit(Category.FAULT, level, trace, entity, action, detail, true, true);
    }

    private static void emit(Category category, ServerLevel level, long trace, Entity entity, String action,
                             String detail, boolean critical, boolean stateEvent) {
        if (!enabled(category)) return;
        boolean watched = entity != null && WATCHED.contains(entity.getUUID());
        if (entity != null && !WATCHED.isEmpty() && !watched) return;
        long tick = level == null ? -1L : level.getGameTime();
        int sample = PerformanceConfig.DEBUG_SAMPLE_EVERY_N.get();
        if (!critical && !stateEvent && !watched && entity != null && sample > 1
                && Math.floorMod(entity.getUUID().getLeastSignificantBits() ^ tick, sample) != 0) return;
        if (!critical && !stateEvent && !watched && !acquireRateSlot()) { DROPPED.incrementAndGet(); return; }
        ensureWriter();
        String type = entity == null ? "" : String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
        String dimension = level == null ? "" : level.dimension().location().toString();
        String pos = entity == null || !PerformanceConfig.DEBUG_INCLUDE_COORDINATES.get() ? ""
                : entity.getX() + "," + entity.getY() + "," + entity.getZ();
        String line = "{\"time\":\"" + Instant.now() + "\",\"session\":\"" + session
                + "\",\"tick\":" + tick + ",\"thread\":\"" + escape(Thread.currentThread().getName())
                + "\",\"category\":\"" + category + "\",\"action\":\"" + escape(action)
                + "\",\"priority\":\"" + (critical ? "fault" : stateEvent ? "state" : "normal")
                + "\",\"trace\":" + trace + ",\"dimension\":\"" + escape(dimension)
                + "\",\"entityId\":" + (entity == null ? -1 : entity.getId())
                + ",\"uuid\":\"" + (entity == null ? "" : entity.getUUID())
                + "\",\"entityType\":\"" + escape(type) + "\",\"pos\":\"" + escape(pos)
                + "\",\"detail\":\"" + escape(detail) + "\"}";
        synchronized (RECENT) {
            RECENT.addLast(line);
            int maximum = PerformanceConfig.DEBUG_RING_ENTRIES.get();
            while (RECENT.size() > maximum) RECENT.removeFirst();
        }
        if (!WRITE_QUEUE.offer(line)) DROPPED.incrementAndGet();
    }

    private static synchronized boolean acquireRateSlot() {
        long second = System.currentTimeMillis() / 1000L;
        if (second != rateSecond) { rateSecond = second; rateCount = 0; }
        if (rateCount >= PerformanceConfig.DEBUG_MAX_EVENTS_PER_SECOND.get()) return false;
        rateCount++;
        return true;
    }

    private static void ensureWriter() {
        if (RUNNING.get() || !RUNNING.compareAndSet(false, true)) return;
        writerThread = new Thread(DebugTrace::writerLoop, "SporePerformance-DebugWriter");
        writerThread.setDaemon(true);
        writerThread.setPriority(Thread.MIN_PRIORITY);
        writerThread.start();
    }

    private static void writerLoop() {
        try {
            Files.createDirectories(FILE.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                while (RUNNING.get() || !WRITE_QUEUE.isEmpty()) {
                    String line = WRITE_QUEUE.poll();
                    if (line == null) { try { Thread.sleep(25L); } catch (InterruptedException ignored) {} continue; }
                    writer.write(line); writer.newLine();
                    if (WRITE_QUEUE.isEmpty()) writer.flush();
                }
            }
        } catch (IOException exception) {
            SporePerformance.LOGGER.error("Spore Performance debug writer failed", exception);
        } finally { RUNNING.set(false); }
    }

    public static void watch(UUID uuid) { WATCHED.add(uuid); }
    public static void unwatch(UUID uuid) { WATCHED.remove(uuid); }
    public static void clearWatch() { WATCHED.clear(); }
    public static Set<UUID> watched() { return Set.copyOf(WATCHED); }
    public static long dropped() { return DROPPED.get(); }
    public static Path file() { return FILE; }
    public static List<String> recent(int count) {
        synchronized (RECENT) {
            List<String> all = new ArrayList<>(RECENT);
            return all.subList(Math.max(0, all.size() - Math.max(1, count)), all.size());
        }
    }
    public static void reset() { synchronized (RECENT) { RECENT.clear(); } DROPPED.set(0L); }
    public static void close() {
        RUNNING.set(false);
        Thread thread = writerThread;
        if (thread != null) thread.interrupt();
        writerThread = null;
        session = UUID.randomUUID().toString();
        clearWatch();
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private DebugTrace() {}
}
