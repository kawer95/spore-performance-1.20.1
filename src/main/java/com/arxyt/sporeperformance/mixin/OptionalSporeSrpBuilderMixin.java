package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.compat.DimensionEntityIndex;
import com.arxyt.sporeperformance.compat.SporeSrpLevelBuckets;
import com.arxyt.sporeperformance.compat.SporeSrpStagger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraftforge.event.TickEvent;

import java.util.UUID;

/** Builder records share the UUID-to-dimension index used by the other sporesrp handlers. */
@Pseudo
@Mixin(targets = "com.maha_fish.sporesrp.handler.GastgaberBuilderHandler", remap = false)
abstract class OptionalSporeSrpBuilderMixin {
    @Inject(method = "onServerTick", at = @At("HEAD"), require = 0)
    private void sporeperformance$beginStagger(TickEvent.ServerTickEvent event, CallbackInfo callback) { SporeSrpStagger.begin(event); }

    @Redirect(method = "onServerTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;m_129785_()Ljava/lang/Iterable;", remap = false), require = 0)
    private Iterable<ServerLevel> sporeperformance$activeBuilderLevels(MinecraftServer server) {
        return SporeSrpLevelBuckets.levelsForBuilders(server, this);
    }

    @Redirect(method = "onServerTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;m_8791_(Ljava/util/UUID;)Lnet/minecraft/world/entity/Entity;", remap = false), require = 0)
    private Entity sporeperformance$guardDimensionLookup(ServerLevel level, UUID id) {
        return DimensionEntityIndex.INSTANCE.getOrNull(level, id);
    }

    @Redirect(method = "onServerTick", at = @At(value = "INVOKE", target = "Ljava/util/Set;iterator()Ljava/util/Iterator;", remap = false), require = 0)
    private java.util.Iterator<UUID> sporeperformance$staggerBuilders(java.util.Set<UUID> builders) {
        return SporeSrpStagger.builderIterator(builders);
    }
}
