package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.compat.DimensionEntityIndex;
import com.arxyt.sporeperformance.compat.SporeSrpLevelBuckets;
import com.arxyt.sporeperformance.compat.SporeSrpStagger;
import com.arxyt.sporeperformance.compat.SporeSrpBackgroundScheduler;
import net.minecraft.core.BlockPos;
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
import java.util.Map;

@Pseudo
@Mixin(targets = "com.maha_fish.sporesrp.handler.ProtoSkillsHandler", remap = false)
abstract class OptionalSporeSrpProtoSkillsMixin {
    /**
     * @author Spore Performance
     * @reason Splits only aggressive-mode surface searches across loaded chunks; the disabled branch is the native scan.
     */
    @org.spongepowered.asm.mixin.Overwrite(remap = false)
    public static BlockPos scanForSurface(ServerLevel level, BlockPos center, int maxRadius) {
        return SporeSrpBackgroundScheduler.INSTANCE.findSurface(level, center, maxRadius, SporeSrpBackgroundScheduler.SurfaceKind.PROTO);
    }

    @Inject(method = "onServerTick", at = @At("HEAD"), require = 0)
    private void sporeperformance$beginStagger(TickEvent.ServerTickEvent event, CallbackInfo callback) { SporeSrpStagger.begin(event); }

    @Redirect(method = "onServerTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;m_129785_()Ljava/lang/Iterable;", remap = false), require = 0)
    private Iterable<ServerLevel> sporeperformance$bucketLevels(MinecraftServer server) { return SporeSrpLevelBuckets.levelsFor(server, this); }

    @Redirect(method = "onServerTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;m_8791_(Ljava/util/UUID;)Lnet/minecraft/world/entity/Entity;", remap = false), require = 0)
    private Entity sporeperformance$guardDimensionLookup(ServerLevel level, UUID id) {
        return DimensionEntityIndex.INSTANCE.getOrNull(level, id);
    }

    @Redirect(method = "onServerTick", at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;", remap = false), require = 0)
    private java.util.Collection<?> sporeperformance$staggerData(Map<?, ?> records) {
        return SporeSrpStagger.dataValues(records, SporeSrpStagger.Kind.PROTO);
    }
}
