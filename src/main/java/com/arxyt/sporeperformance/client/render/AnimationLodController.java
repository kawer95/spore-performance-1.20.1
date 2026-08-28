package com.arxyt.sporeperformance.client.render;

import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Restores per-entity model poses between deliberately staggered setupAnim evaluations. */
public final class AnimationLodController {
    private static final IdentityHashMap<EntityModel<?>, ModelStructure> STRUCTURES = new IdentityHashMap<>();
    private static final BoundedLruCache<CacheKey, PoseSnapshot> SNAPSHOTS = new BoundedLruCache<>(512);
    private static final Set<Class<?>> FAILED_MODELS = Collections.newSetFromMap(new IdentityHashMap<>());

    public static boolean restoreIfScheduled(EntityModel<?> model, Entity entity) {
        int interval = interval(model, entity);
        if (interval <= 1 || emergency(entity)) return false;
        SNAPSHOTS.setCapacity(PerformanceConfig.CLIENT_POSE_CACHE_MAX_ENTITIES.get());
        CacheKey key = new CacheKey(model, entity.getId());
        PoseSnapshot snapshot = SNAPSHOTS.get(key);
        if (snapshot == null || snapshot.stateSignature != stateSignature(entity)) {
            ClientRenderMetrics.increment("animation.emergency_state_refresh");
            return false;
        }
        long phasedFrame = ClientRenderFrameClock.frame() + Integer.toUnsignedLong(entity.getId() * 0x9E3779B9);
        if (Math.floorMod(phasedFrame, interval) == 0L) return false;
        snapshot.restore();
        ClientRenderMetrics.increment("animation.pose_reused");
        if (ClientRenderMetrics.enabled()) {
            ClientRenderMetrics.increment("animation.pose_reused." + SporeRenderClassifier.category(entity).name().toLowerCase(java.util.Locale.ROOT));
        }
        return true;
    }

    public static void captureAfterSetup(EntityModel<?> model, Entity entity) {
        int interval = interval(model, entity);
        if (interval <= 1) return;
        ModelStructure structure = structure(model);
        if (structure == null || structure.parts.isEmpty()) return;
        int capacity = PerformanceConfig.CLIENT_POSE_CACHE_MAX_ENTITIES.get();
        SNAPSHOTS.setCapacity(capacity);
        CacheKey key = new CacheKey(model, entity.getId());
        if (!SNAPSHOTS.containsKey(key) && SNAPSHOTS.size() >= capacity) {
            ClientRenderMetrics.increment("animation.pose_cache_full_fallback");
            return;
        }
        SNAPSHOTS.put(key, PoseSnapshot.capture(structure.parts, stateSignature(entity)));
        ClientRenderMetrics.increment("animation.pose_computed");
        if (ClientRenderMetrics.enabled()) {
            ClientRenderMetrics.increment("animation.pose_computed." + SporeRenderClassifier.category(entity).name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    public static void removeEntity(int entityId) {
        SNAPSHOTS.removeIf(key -> key.entityId == entityId);
    }

    public static int cacheSize() {
        return SNAPSHOTS.size();
    }

    public static void clear() {
        SNAPSHOTS.clear();
        STRUCTURES.clear();
        FAILED_MODELS.clear();
    }

    static int intervalForDistance(boolean major, double distanceSquared,
                                   int near, int medium, int far,
                                   int mediumInterval, int farInterval, int veryFarInterval,
                                   boolean majorEnabled, int majorNear, int majorFar, int majorFarInterval) {
        if (major) {
            if (!majorEnabled || distanceSquared <= (double) majorNear * majorNear) return 1;
            if (distanceSquared <= (double) majorFar * majorFar) return Math.min(2, Math.max(1, majorFarInterval));
            return Math.max(1, majorFarInterval);
        }
        if (distanceSquared <= (double) near * near) return 1;
        if (distanceSquared <= (double) medium * medium) return Math.max(1, mediumInterval);
        if (distanceSquared <= (double) far * far) return Math.max(1, farInterval);
        return Math.max(1, veryFarInterval);
    }

    private static int interval(EntityModel<?> model, Entity entity) {
        if (!PerformanceConfig.CLIENT_ANIMATION_LOD.get()
                || !SporeRenderClassifier.isSporeEntity(entity)
                || !SporeRenderClassifier.isSporeModel(model)) return 1;
        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (camera == null || camera == entity) return 1;
        SporeRenderClassifier.Category category = SporeRenderClassifier.category(entity);
        boolean major = category != SporeRenderClassifier.Category.NORMAL;
        boolean majorEnabled = switch (category) {
            case CALAMITY -> PerformanceConfig.CLIENT_MAJOR_ANIMATION_LOD.get() || PerformanceConfig.CLIENT_CALAMITY_ANIMATION_LOD.get();
            case ORGANOID -> PerformanceConfig.CLIENT_MAJOR_ANIMATION_LOD.get() || PerformanceConfig.CLIENT_ORGANOID_ANIMATION_LOD.get();
            case HYPER -> PerformanceConfig.CLIENT_MAJOR_ANIMATION_LOD.get() || PerformanceConfig.CLIENT_HYPER_ANIMATION_LOD.get();
            case PROTO -> PerformanceConfig.CLIENT_MAJOR_ANIMATION_LOD.get() || PerformanceConfig.CLIENT_PROTO_ANIMATION_LOD.get();
            case NORMAL -> false;
        };
        return intervalForDistance(major, camera.distanceToSqr(entity),
                PerformanceConfig.CLIENT_ANIMATION_NEAR_DISTANCE.get(),
                PerformanceConfig.CLIENT_ANIMATION_MEDIUM_DISTANCE.get(),
                PerformanceConfig.CLIENT_ANIMATION_FAR_DISTANCE.get(),
                PerformanceConfig.CLIENT_ANIMATION_MEDIUM_INTERVAL.get(),
                PerformanceConfig.CLIENT_ANIMATION_FAR_INTERVAL.get(),
                PerformanceConfig.CLIENT_ANIMATION_VERY_FAR_INTERVAL.get(),
                majorEnabled,
                PerformanceConfig.CLIENT_MAJOR_ANIMATION_NEAR_DISTANCE.get(),
                PerformanceConfig.CLIENT_MAJOR_ANIMATION_FAR_DISTANCE.get(),
                PerformanceConfig.CLIENT_MAJOR_ANIMATION_FAR_INTERVAL.get());
    }

    private static boolean emergency(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return true;
        return living.tickCount < 20 || living.hurtTime > 0 || living.deathTime > 0 || living.swinging
                || living.isPassenger() || living.isCrouching() || living.isSwimming() || living.isFallFlying();
    }

    private static long stateSignature(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return 0;
        long state = living.getPose().ordinal();
        state = 31 * state + (living.isPassenger() ? 1 : 0);
        state = 31 * state + (living.isCrouching() ? 1 : 0);
        state = 31 * state + (living.isSwimming() ? 1 : 0);
        state = 31 * state + (living.isFallFlying() ? 1 : 0);
        state = 31 * state + (living.swinging ? 1 : 0);
        state = 31 * state + Integer.signum(living.hurtTime);
        state = 31 * state + Integer.signum(living.deathTime);
        if (living.getEntityData() instanceof SynchedDataVersion versioned) {
            state = 31 * state + versioned.sporePerformance$dataVersion();
        }
        return state;
    }

    private static ModelStructure structure(EntityModel<?> model) {
        ModelStructure existing = STRUCTURES.get(model);
        if (existing != null) return existing;
        if (FAILED_MODELS.contains(model.getClass())) return null;
        try {
            Set<ModelPart> unique = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Class<?> type = model.getClass(); type != null && EntityModel.class.isAssignableFrom(type); type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (!ModelPart.class.isAssignableFrom(field.getType())) continue;
                    if (!field.trySetAccessible()) continue;
                    ModelPart root = (ModelPart) field.get(model);
                    if (root != null) root.getAllParts().forEach(unique::add);
                }
            }
            ModelStructure created = new ModelStructure(List.copyOf(unique));
            STRUCTURES.put(model, created);
            ClientRenderMetrics.add("animation.model_parts_discovered", created.parts.size());
            return created;
        } catch (ReflectiveOperationException | LinkageError exception) {
            FAILED_MODELS.add(model.getClass());
            SporePerformance.LOGGER.warn("Animation LOD disabled for incompatible model {}", model.getClass().getName(), exception);
            return null;
        }
    }

    private record ModelStructure(List<ModelPart> parts) {}

    private static final class CacheKey {
        private final EntityModel<?> model;
        private final int entityId;

        private CacheKey(EntityModel<?> model, int entityId) {
            this.model = model;
            this.entityId = entityId;
        }

        @Override public boolean equals(Object object) {
            return object instanceof CacheKey other && model == other.model && entityId == other.entityId;
        }

        @Override public int hashCode() {
            return 31 * System.identityHashCode(model) + entityId;
        }
    }

    private static final class PoseSnapshot {
        private final List<PartState> states;
        private final long stateSignature;

        private PoseSnapshot(List<PartState> states, long stateSignature) {
            this.states = states;
            this.stateSignature = stateSignature;
        }

        private static PoseSnapshot capture(List<ModelPart> parts, long stateSignature) {
            List<PartState> states = new ArrayList<>(parts.size());
            for (ModelPart part : parts) states.add(new PartState(part, part.storePose(), part.visible, part.skipDraw));
            return new PoseSnapshot(states, stateSignature);
        }

        private void restore() {
            for (PartState state : states) {
                state.part.loadPose(state.pose);
                state.part.visible = state.visible;
                state.part.skipDraw = state.skipDraw;
            }
        }
    }

    private record PartState(ModelPart part, PartPose pose, boolean visible, boolean skipDraw) {}

    private AnimationLodController() {}
}
