package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.SBlockEntities.CDUBlockEntity;
import com.arxyt.sporeperformance.compat.BlockEntitySync;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Synchronizes CDU start/stop render state while leaving its per-tick fuel countdown server-side. */
@Mixin(value = CDUBlockEntity.class, remap = false)
abstract class CduBlockEntitySyncMixin {
    @Inject(method = "setFuel", at = @At("RETURN"))
    private void sporeperformance$syncAssignedFuel(int value, CallbackInfo callback) {
        BlockEntitySync.send((BlockEntity) (Object) this);
    }

    @Redirect(method = "serverTick", at = @At(value = "FIELD",
            target = "Lcom/Harbinger/Spore/SBlockEntities/CDUBlockEntity;fuel:I",
            opcode = Opcodes.PUTFIELD, remap = false), require = 1)
    private static void sporeperformance$syncWhenFuelRunsOut(CDUBlockEntity entity, int value) {
        entity.fuel = value;
        if (value == 0) BlockEntitySync.send(entity);
    }
}
