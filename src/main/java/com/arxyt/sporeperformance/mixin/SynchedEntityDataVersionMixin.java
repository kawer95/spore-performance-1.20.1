package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.client.render.SynchedDataVersion;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

/** Invalidates cached poses immediately when an entity's synchronized skill or state data changes. */
@Mixin(SynchedEntityData.class)
public abstract class SynchedEntityDataVersionMixin implements SynchedDataVersion {
    @Unique private long sporePerformance$dataVersion;

    @Inject(method = "set(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V", at = @At("HEAD"))
    private <T> void sporePerformance$trackChange(EntityDataAccessor<T> accessor, T value,
                                                   boolean force, CallbackInfo callback) {
        SynchedEntityData self = (SynchedEntityData) (Object) this;
        if (force || !Objects.equals(self.get(accessor), value)) sporePerformance$dataVersion++;
    }

    @Override
    public long sporePerformance$dataVersion() {
        return sporePerformance$dataVersion;
    }
}
