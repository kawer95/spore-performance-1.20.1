package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.Utility.TumoroidNuke;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Keeps Tumoroid Nuke's timer and flags out of Hinderburg's metadata id range. */
@Mixin(value = TumoroidNuke.class, remap = false)
abstract class TumoroidNukeEntityDataOwnerMixin {
    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/syncher/SynchedEntityData;m_135353_(Ljava/lang/Class;Lnet/minecraft/network/syncher/EntityDataSerializer;)Lnet/minecraft/network/syncher/EntityDataAccessor;",
            remap = false), index = 0, require = 3, allow = 3)
    private static Class<? extends Entity> sporeperformance$ownAccessors(Class<? extends Entity> ignored) {
        return TumoroidNuke.class;
    }
}
