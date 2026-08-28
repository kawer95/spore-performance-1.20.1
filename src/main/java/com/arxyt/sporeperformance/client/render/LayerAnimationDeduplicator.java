package com.arxyt.sporeperformance.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.world.entity.Entity;

import java.util.IdentityHashMap;

/** Exact-frame ledger proving a layer setupAnim call is identical to its parent renderer call. */
public final class LayerAnimationDeduplicator {
    private static final IdentityHashMap<EntityModel<?>, SetupCall> LAST_PARENT_SETUP = new IdentityHashMap<>();

    public static void record(EntityModel<?> model, Entity entity, float limbSwing, float limbAmount,
                              float age, float yaw, float pitch) {
        LAST_PARENT_SETUP.put(model, SetupCall.of(entity, limbSwing, limbAmount, age, yaw, pitch));
    }

    public static boolean isExactDuplicate(EntityModel<?> model, Entity entity, float limbSwing, float limbAmount,
                                           float age, float yaw, float pitch) {
        SetupCall call = LAST_PARENT_SETUP.get(model);
        return call != null && call.equals(SetupCall.of(entity, limbSwing, limbAmount, age, yaw, pitch));
    }

    public static void clear() {
        LAST_PARENT_SETUP.clear();
    }

    private record SetupCall(long frame, int entityId, int limbSwing, int limbAmount, int age, int yaw, int pitch) {
        private static SetupCall of(Entity entity, float limbSwing, float limbAmount, float age, float yaw, float pitch) {
            return new SetupCall(ClientRenderFrameClock.frame(), entity.getId(),
                    Float.floatToRawIntBits(limbSwing), Float.floatToRawIntBits(limbAmount),
                    Float.floatToRawIntBits(age), Float.floatToRawIntBits(yaw), Float.floatToRawIntBits(pitch));
        }
    }

    private LayerAnimationDeduplicator() {}
}
