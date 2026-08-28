package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.compat.DimensionEntityIndex;
import com.arxyt.sporeperformance.compat.SporeSrpLevelBuckets;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

@Pseudo
@Mixin(targets = "com.maha_fish.sporesrp.handler.ProtoMarkedMoundHandler", remap = false)
abstract class OptionalSporeSrpMarkedMoundMixin {
    @Redirect(method = "onServerTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;m_129785_()Ljava/lang/Iterable;", remap = false), require = 0)
    private Iterable<ServerLevel> sporeperformance$bucketLevels(MinecraftServer server) { return SporeSrpLevelBuckets.levelsFor(server, this); }

    @Redirect(method = "onServerTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;m_8791_(Ljava/util/UUID;)Lnet/minecraft/world/entity/Entity;", remap = false), require = 0)
    private Entity sporeperformance$guardDimensionLookup(ServerLevel level, UUID id) {
        return DimensionEntityIndex.INSTANCE.getOrNull(level, id);
    }
}
