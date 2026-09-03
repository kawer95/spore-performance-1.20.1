package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.BaseEntities.Hyper;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Keeps Hyper's nest accessor out of Infected's shared metadata id range. */
@Mixin(value = Hyper.class, remap = false)
abstract class HyperEntityDataOwnerMixin {
    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/syncher/SynchedEntityData;m_135353_(Ljava/lang/Class;Lnet/minecraft/network/syncher/EntityDataSerializer;)Lnet/minecraft/network/syncher/EntityDataAccessor;",
            remap = false), index = 0, require = 1)
    private static Class<? extends Entity> sporeperformance$ownNestAccessor(Class<? extends Entity> ignored) {
        return Hyper.class;
    }
}
