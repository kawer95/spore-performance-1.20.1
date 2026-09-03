package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.EvolvedInfected.Howler;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Registers Howler's variant accessor against Howler rather than Spitter. */
@Mixin(value = Howler.class, remap = false)
abstract class HowlerEntityDataOwnerMixin {
    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/syncher/SynchedEntityData;m_135353_(Ljava/lang/Class;Lnet/minecraft/network/syncher/EntityDataSerializer;)Lnet/minecraft/network/syncher/EntityDataAccessor;",
            remap = false), index = 0, require = 1)
    private static Class<? extends Entity> sporeperformance$ownVariantAccessor(Class<? extends Entity> ignored) {
        return Howler.class;
    }
}
