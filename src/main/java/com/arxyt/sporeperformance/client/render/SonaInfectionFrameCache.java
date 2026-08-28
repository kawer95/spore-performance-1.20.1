package com.arxyt.sporeperformance.client.render;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.SporePerformance;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Exact-frame cache shared by Sona's overlay and post renderer. */
public final class SonaInfectionFrameCache {
    private static final MethodType CAN_CHUNK_TYPE = MethodType.methodType(boolean.class, Level.class);
    private static final MethodType AVERAGE_TYPE = MethodType.methodType(double.class, Level.class, Vec3.class);
    private static final MethodType COLOR_TYPE = MethodType.methodType(Vec3.class, Vec3.class, Vec3.class, ClientLevel.class);
    private static volatile boolean methodsResolved;
    private static volatile boolean methodsAvailable;
    private static MethodHandle canChunkMethod;
    private static MethodHandle averageMethod;
    private static MethodHandle colorMethod;
    private static long frame = Long.MIN_VALUE;
    private static Level level;
    private static Vec3 cameraPosition;
    private static boolean hasCanChunk;
    private static boolean canChunk;
    private static boolean hasAverage;
    private static double average;
    private static Vec3 colorBase;
    private static Vec3 color;
    private static boolean hasColor;

    public static boolean canChunk(Level currentLevel) {
        if (!PerformanceConfig.CLIENT_SONA_SHARE_FRAME_SAMPLE.get()) {
            return invokeCanChunk(currentLevel);
        }
        prepare(currentLevel, null);
        if (!hasCanChunk) {
            canChunk = invokeCanChunk(currentLevel);
            hasCanChunk = true;
            ClientRenderMetrics.increment("sona.sample.can_chunk_computed");
        } else {
            ClientRenderMetrics.increment("sona.sample.can_chunk_reused");
        }
        return canChunk;
    }

    public static double average(Level currentLevel, Vec3 currentCameraPosition) {
        if (!PerformanceConfig.CLIENT_SONA_SHARE_FRAME_SAMPLE.get()) {
            return invokeAverage(currentLevel, currentCameraPosition);
        }
        prepare(currentLevel, currentCameraPosition);
        if (!hasAverage) {
            average = invokeAverage(currentLevel, currentCameraPosition);
            hasAverage = true;
            ClientRenderMetrics.increment("sona.sample.average_computed");
        } else {
            ClientRenderMetrics.increment("sona.sample.average_reused");
        }
        return average;
    }

    public static Vec3 fogColor(Vec3 base, Vec3 currentCameraPosition, ClientLevel currentLevel) {
        if (!PerformanceConfig.CLIENT_SONA_SHARE_FRAME_SAMPLE.get()) {
            return invokeColor(base, currentCameraPosition, currentLevel);
        }
        prepare(currentLevel, currentCameraPosition);
        if (!hasColor || !base.equals(colorBase)) {
            colorBase = base;
            color = invokeColor(base, currentCameraPosition, currentLevel);
            hasColor = true;
            ClientRenderMetrics.increment("sona.sample.color_computed");
        } else {
            ClientRenderMetrics.increment("sona.sample.color_reused");
        }
        return color;
    }

    public static void clear() {
        frame = Long.MIN_VALUE;
        level = null;
        cameraPosition = null;
        hasCanChunk = false;
        hasAverage = false;
        hasColor = false;
        colorBase = null;
        color = null;
    }

    private static void prepare(Level currentLevel, Vec3 currentCameraPosition) {
        long currentFrame = ClientRenderFrameClock.frame();
        boolean positionChanged = currentCameraPosition != null && cameraPosition != null
                && !currentCameraPosition.equals(cameraPosition);
        if (frame != currentFrame || level != currentLevel || positionChanged) {
            frame = currentFrame;
            level = currentLevel;
            cameraPosition = currentCameraPosition;
            hasCanChunk = false;
            hasAverage = false;
            hasColor = false;
            colorBase = null;
            color = null;
        } else if (cameraPosition == null && currentCameraPosition != null) {
            cameraPosition = currentCameraPosition;
        }
    }

    /** Resolves Sona once. Optional client integrations must not put Sona on the compile classpath. */
    private static void resolveMethods() {
        if (methodsResolved) return;
        synchronized (SonaInfectionFrameCache.class) {
            if (methodsResolved) return;
            try {
                Class<?> manager = Class.forName("com.scarasol.sona.manager.InfectionManager", false,
                        SonaInfectionFrameCache.class.getClassLoader());
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                canChunkMethod = lookup.findStatic(manager, "canChunkInfection", CAN_CHUNK_TYPE);
                averageMethod = lookup.findStatic(manager, "getAveZoneInfectionInRender", AVERAGE_TYPE);
                colorMethod = lookup.findStatic(manager, "getInfectionChunkFogColor", COLOR_TYPE);
                methodsAvailable = true;
            } catch (Throwable failure) {
                methodsAvailable = false;
                SporePerformance.LOGGER.warn("Sona infection methods unavailable; shared frame sampling disabled", failure);
            } finally {
                methodsResolved = true;
            }
        }
    }

    private static boolean invokeCanChunk(Level currentLevel) {
        resolveMethods();
        if (!methodsAvailable) return false;
        try { return (boolean) canChunkMethod.invoke(currentLevel); }
        catch (Throwable failure) { disable(failure); return false; }
    }

    private static double invokeAverage(Level currentLevel, Vec3 currentCameraPosition) {
        resolveMethods();
        if (!methodsAvailable) return 0.0D;
        try { return (double) averageMethod.invoke(currentLevel, currentCameraPosition); }
        catch (Throwable failure) { disable(failure); return 0.0D; }
    }

    private static Vec3 invokeColor(Vec3 base, Vec3 currentCameraPosition, ClientLevel currentLevel) {
        resolveMethods();
        if (!methodsAvailable) return base;
        try { return (Vec3) colorMethod.invoke(base, currentCameraPosition, currentLevel); }
        catch (Throwable failure) { disable(failure); return base; }
    }

    private static void disable(Throwable failure) {
        methodsAvailable = false;
        SporePerformance.LOGGER.warn("Sona infection method invocation failed; integration disabled", failure);
    }

    private SonaInfectionFrameCache() {}
}
