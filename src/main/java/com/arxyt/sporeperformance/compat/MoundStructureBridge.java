package com.arxyt.sporeperformance.compat;

import com.Harbinger.Spore.Sentities.Organoids.Mound;
import com.arxyt.sporeperformance.SporePerformance;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;

/** Cached private-state bridge for Mound's structure-placement flag. */
public final class MoundStructureBridge {
    private static volatile boolean attempted;
    private static volatile VarHandle entityData;
    private static volatile EntityDataAccessor<Boolean> structure;

    public static void initialize() {
        if (attempted) return;
        synchronized (MoundStructureBridge.class) {
            if (attempted) return;
            attempted = true;
            try {
                Field structureField = Mound.class.getDeclaredField("STRUCTURE");
                structureField.setAccessible(true);
                @SuppressWarnings("unchecked") EntityDataAccessor<Boolean> accessor = (EntityDataAccessor<Boolean>) structureField.get(null);
                Field dataField = findEntityDataField(Mound.class);
                entityData = MethodHandles.privateLookupIn(dataField.getDeclaringClass(), MethodHandles.lookup()).unreflectVarHandle(dataField);
                structure = accessor;
            } catch (ReflectiveOperationException | RuntimeException exception) {
                SporePerformance.LOGGER.warn("Mound structure bridge unavailable; aggressive placement stage disabled", exception);
                entityData = null;
                structure = null;
            }
        }
    }

    public static boolean hasStructureSlot(Mound mound) {
        initialize();
        VarHandle handle = entityData;
        EntityDataAccessor<Boolean> accessor = structure;
        return handle != null && accessor != null && ((SynchedEntityData) handle.get(mound)).get(accessor);
    }

    public static void consumeStructureSlot(Mound mound) {
        VarHandle handle = entityData;
        EntityDataAccessor<Boolean> accessor = structure;
        if (handle != null && accessor != null) ((SynchedEntityData) handle.get(mound)).set(accessor, false);
    }

    private static Field findEntityDataField(Class<?> type) throws NoSuchFieldException {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            for (Field field : cursor.getDeclaredFields()) if (field.getType() == SynchedEntityData.class) return field;
        }
        throw new NoSuchFieldException("SynchedEntityData");
    }

    private MoundStructureBridge() {}
}
