package com.arxyt.sporeperformance.client;

import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Client-only owner of fungal-decoration mesh culling state.
 *
 * <p>Chunk workers only read volatile camera/config snapshots and add section keys. The client
 * thread owns boundary classification and bounded rebuild submission. No Level, BlockEntity or
 * entity reference is retained, so changing worlds cannot pin client world state.</p>
 */
@Mod.EventBusSubscriber(modid = SporePerformance.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FungalDecorationCulling {
    private static final List<ResourceLocation> TARGET_IDS = List.of(
            id("biomass_bulb"),
            id("blomfung"),
            id("bloomfung2"),
            id("exploding_lump"),
            id("fang_lump"),
            id("fungal_clamp"),
            id("fungal_stem_sapling"),
            id("fungal_stem"),
            id("fungal_stem_top"),
            id("growth_mycelium"),
            id("growths_small"),
            id("remains"),
            id("bile_lump")
    );
    private static final Set<Long> TARGET_SECTIONS = ConcurrentHashMap.newKeySet();
    private static final Set<Long> PENDING_REBUILDS = ConcurrentHashMap.newKeySet();
    private static final ConcurrentLinkedQueue<Long> REBUILD_QUEUE = new ConcurrentLinkedQueue<>();

    private static volatile Set<Block> targets = Set.of();
    private static volatile boolean targetsResolved;
    private static volatile CullSnapshot cullSnapshot = CullSnapshot.disabled();

    private static Object currentLevel;
    private static boolean lastConfiguredEnabled;
    private static int lastDistance = 32;
    private static int lastCommandDistance = 128;
    private static boolean lastCommandCameraMode;

    /** Called from Embeddium chunk worker threads. */
    public static boolean shouldCull(BlockState state, BlockPos pos) {
        Set<Block> currentTargets = targetsResolved ? targets : ensureTargetsResolved();
        if (!currentTargets.contains(state.getBlock())) return false;
        TARGET_SECTIONS.add(SectionPos.asLong(pos));
        CullSnapshot snapshot = cullSnapshot;
        if (!snapshot.enabled || !snapshot.cameraReady) return false;
        double dx = pos.getX() + 0.5D - snapshot.cameraX;
        double dy = pos.getY() + 0.5D - snapshot.cameraY;
        double dz = pos.getZ() + 0.5D - snapshot.cameraZ;
        return dx * dx + dy * dy + dz * dz > snapshot.renderDistanceSqr;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            clear();
            return;
        }
        if (currentLevel != minecraft.level) {
            clear();
            currentLevel = minecraft.level;
            resolveTargets();
        } else if (targets.isEmpty()) {
            resolveTargets();
        }

        boolean configuredEnabled = PerformanceConfig.CLIENT_FUNGAL_DECORATION_DISTANCE_CULL.get();
        int configuredDistance = PerformanceConfig.CLIENT_FUNGAL_DECORATION_DISTANCE.get();
        int configuredCommandDistance = PerformanceConfig.CLIENT_FUNGAL_DECORATION_COMMAND_DISTANCE.get();
        boolean commandCameraMode = configuredEnabled && DominionSwordCameraBridge.detachedCameraActive();
        int effectiveDistance = commandCameraMode ? configuredCommandDistance : configuredDistance;
        if (configuredEnabled != lastConfiguredEnabled || configuredDistance != lastDistance
                || configuredCommandDistance != lastCommandDistance) {
            lastConfiguredEnabled = configuredEnabled;
            lastDistance = configuredDistance;
            lastCommandDistance = configuredCommandDistance;
            CullSnapshot old = cullSnapshot;
            cullSnapshot = new CullSnapshot(configuredEnabled, old.cameraReady, old.cameraX, old.cameraY,
                    old.cameraZ, (double) effectiveDistance * effectiveDistance);
            enqueueAllKnownSections();
        }

        Vec3 position = selectViewpoint(commandCameraMode, minecraft.player.getEyePosition(),
                minecraft.gameRenderer.getMainCamera().getPosition());
        int step = PerformanceConfig.CLIENT_FUNGAL_DECORATION_CAMERA_STEP.get();
        CullSnapshot old = cullSnapshot;
        if (commandCameraMode != lastCommandCameraMode) {
            lastCommandCameraMode = commandCameraMode;
            setCamera(position, effectiveDistance);
            enqueueAllKnownSections();
        } else if (!old.cameraReady) {
            setCamera(position, effectiveDistance);
            enqueueAllKnownSections();
        } else if (position.distanceToSqr(old.cameraX, old.cameraY, old.cameraZ) >= (double) step * step) {
            setCamera(position, effectiveDistance);
            if (old.enabled) enqueueBoundarySections(old.cameraX, old.cameraY, old.cameraZ,
                    position.x, position.y, position.z, effectiveDistance);
        }

        drainRebuildQueue(minecraft, PerformanceConfig.CLIENT_FUNGAL_DECORATION_REBUILDS_PER_TICK.get());
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        int chunkX = event.getChunk().getPos().x;
        int chunkZ = event.getChunk().getPos().z;
        TARGET_SECTIONS.removeIf(key -> SectionPos.x(key) == chunkX && SectionPos.z(key) == chunkZ);
        PENDING_REBUILDS.removeIf(key -> SectionPos.x(key) == chunkX && SectionPos.z(key) == chunkZ);
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear();
    }

    static int sectionRelation(int sectionX, int sectionY, int sectionZ,
                               double pointX, double pointY, double pointZ, double radius) {
        double minX = sectionX * 16.0D + 0.5D;
        double minY = sectionY * 16.0D + 0.5D;
        double minZ = sectionZ * 16.0D + 0.5D;
        double maxX = minX + 15.0D;
        double maxY = minY + 15.0D;
        double maxZ = minZ + 15.0D;
        double minDistance = axisDistance(pointX, minX, maxX, false)
                + axisDistance(pointY, minY, maxY, false)
                + axisDistance(pointZ, minZ, maxZ, false);
        double maxDistance = axisDistance(pointX, minX, maxX, true)
                + axisDistance(pointY, minY, maxY, true)
                + axisDistance(pointZ, minZ, maxZ, true);
        double radiusSqr = radius * radius;
        if (maxDistance <= radiusSqr) return -1; // all block centres are visible
        if (minDistance > radiusSqr) return 1;   // all block centres are culled
        return 0;                                // section crosses the boundary
    }

    /** Selects the culling centre without coupling tests or normal mode to a camera mod. */
    static Vec3 selectViewpoint(boolean commandCameraMode, Vec3 playerEye, Vec3 renderedCamera) {
        if (commandCameraMode && renderedCamera != null
                && Double.isFinite(renderedCamera.x)
                && Double.isFinite(renderedCamera.y)
                && Double.isFinite(renderedCamera.z)) {
            return renderedCamera;
        }
        return playerEye;
    }

    private static double axisDistance(double point, double min, double max, boolean farthest) {
        double delta;
        if (farthest) {
            delta = Math.max(Math.abs(point - min), Math.abs(point - max));
        } else if (point < min) {
            delta = min - point;
        } else if (point > max) {
            delta = point - max;
        } else {
            delta = 0.0D;
        }
        return delta * delta;
    }

    private static void enqueueBoundarySections(double oldX, double oldY, double oldZ,
                                                double newX, double newY, double newZ, int radius) {
        for (long key : TARGET_SECTIONS) {
            int sectionX = SectionPos.x(key);
            int sectionY = SectionPos.y(key);
            int sectionZ = SectionPos.z(key);
            int oldRelation = sectionRelation(sectionX, sectionY, sectionZ, oldX, oldY, oldZ, radius);
            int newRelation = sectionRelation(sectionX, sectionY, sectionZ, newX, newY, newZ, radius);
            if (oldRelation == 0 || newRelation == 0 || oldRelation != newRelation) enqueue(key);
        }
    }

    private static void enqueueAllKnownSections() {
        TARGET_SECTIONS.forEach(FungalDecorationCulling::enqueue);
    }

    private static void enqueue(long key) {
        if (PENDING_REBUILDS.add(key)) REBUILD_QUEUE.add(key);
    }

    private static void drainRebuildQueue(Minecraft minecraft, int budget) {
        for (int count = 0; count < budget; count++) {
            Long key = REBUILD_QUEUE.poll();
            if (key == null) return;
            PENDING_REBUILDS.remove(key);
            int sectionX = SectionPos.x(key);
            int sectionY = SectionPos.y(key);
            int sectionZ = SectionPos.z(key);
            if (!minecraft.level.hasChunk(sectionX, sectionZ)) {
                TARGET_SECTIONS.remove(key);
                continue;
            }
            minecraft.levelRenderer.setSectionDirty(sectionX, sectionY, sectionZ);
        }
    }

    private static void resolveTargets() {
        ensureTargetsResolved();
    }

    /**
     * The first asynchronous mesh build may precede the first client tick. Resolve lazily at that
     * first hook invocation, after Forge registries are bootstrapped, and publish one immutable set.
     */
    private static Set<Block> ensureTargetsResolved() {
        if (targetsResolved) return targets;
        synchronized (FungalDecorationCulling.class) {
            if (targetsResolved) return targets;
            targets = resolveTargetSet();
            targetsResolved = true;
            return targets;
        }
    }

    private static Set<Block> resolveTargetSet() {
        Set<Block> resolved = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ResourceLocation> missing = new ArrayList<>();
        for (ResourceLocation id : TARGET_IDS) {
            if (BuiltInRegistries.BLOCK.containsKey(id)) resolved.add(BuiltInRegistries.BLOCK.get(id));
            else missing.add(id);
        }
        Set<Block> result = Collections.unmodifiableSet(resolved);
        if (!missing.isEmpty()) SporePerformance.LOGGER.warn("Fungal decoration culling could not resolve blocks: {}", missing);
        return result;
    }

    private static void setCamera(Vec3 position, int renderDistance) {
        CullSnapshot old = cullSnapshot;
        cullSnapshot = new CullSnapshot(old.enabled, true, position.x, position.y, position.z,
                (double) renderDistance * renderDistance);
    }

    private static void clear() {
        currentLevel = null;
        cullSnapshot = CullSnapshot.disabled();
        lastConfiguredEnabled = false;
        lastDistance = 32;
        lastCommandDistance = 128;
        lastCommandCameraMode = false;
        // Registries outlive a client world. Keep the resolved singleton Block references so the
        // next world's first asynchronous mesh build can be indexed before its first client tick.
        TARGET_SECTIONS.clear();
        PENDING_REBUILDS.clear();
        REBUILD_QUEUE.clear();
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("spore", path);
    }

    /** Immutable publication boundary between the client thread and asynchronous mesh workers. */
    private record CullSnapshot(boolean enabled, boolean cameraReady, double cameraX, double cameraY,
                                double cameraZ, double renderDistanceSqr) {
        private static CullSnapshot disabled() {
            return new CullSnapshot(false, false, 0.0D, 0.0D, 0.0D, 32.0D * 32.0D);
        }
    }

    private FungalDecorationCulling() {}
}
