package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.client.HinderburgClientIndex;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The optional AI Fix methods scan the whole render entity list but use only Hinderburg instances. */
@Pseudo
@Mixin(targets = "com.exhuashan.sporeaifix.client.StormFortressClient", remap = false)
abstract class OptionalStormFortressClientMixin {
    @Redirect(method = {"spawnPersistentAura", "renderStars"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;m_104735_()Ljava/lang/Iterable;", remap = false), require = 0)
    private static Iterable<Entity> sporeperformance$hinderburgsOnly(ClientLevel level) {
        return HinderburgClientIndex.snapshot();
    }
}
