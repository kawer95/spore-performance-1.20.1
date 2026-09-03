package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.Organoids.Vigil;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Registers Vigil's fourth accessor against Vigil rather than Busser. */
@Mixin(value = Vigil.class, remap = false)
abstract class VigilEntityDataOwnerMixin {
    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/syncher/SynchedEntityData;m_135353_(Ljava/lang/Class;Lnet/minecraft/network/syncher/EntityDataSerializer;)Lnet/minecraft/network/syncher/EntityDataAccessor;",
            ordinal = 3, remap = false), index = 0, require = 1)
    private static Class<? extends Entity> sporeperformance$ownVariantAccessor(Class<? extends Entity> ignored) {
        return Vigil.class;
    }
}
