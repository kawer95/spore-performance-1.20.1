package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.client.render.SonaInfectionFrameCache;
import com.arxyt.sporeperformance.client.render.SonaSporeOverlayBatch;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderGuiEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shared sampling and one-batch rendering for Sona's deterministic GUI spores. */
@Pseudo
@Mixin(targets = "com.scarasol.sona.client.renderer.InfectionSporeOverlayRenderer", remap = false)
public abstract class OptionalSonaInfectionOverlayMixin {
    @Redirect(method = "onRenderInfectionSpores", at = @At(value = "INVOKE",
            target = "Lcom/scarasol/sona/manager/InfectionManager;canChunkInfection(Lnet/minecraft/world/level/Level;)Z"),
            remap = false)
    private static boolean sporePerformance$shareCanChunk(Level level) {
        return SonaInfectionFrameCache.canChunk(level);
    }

    @Redirect(method = "onRenderInfectionSpores", at = @At(value = "INVOKE",
            target = "Lcom/scarasol/sona/manager/InfectionManager;getAveZoneInfectionInRender(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;)D"),
            remap = false)
    private static double sporePerformance$shareAverage(Level level, Vec3 cameraPosition) {
        return SonaInfectionFrameCache.average(level, cameraPosition);
    }

    @Redirect(method = "onRenderInfectionSpores", at = @At(value = "INVOKE",
            target = "Lcom/scarasol/sona/manager/InfectionManager;getInfectionChunkFogColor(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/multiplayer/ClientLevel;)Lnet/minecraft/world/phys/Vec3;"),
            remap = false)
    private static Vec3 sporePerformance$shareColor(Vec3 base, Vec3 cameraPosition, ClientLevel level) {
        return SonaInfectionFrameCache.fogColor(base, cameraPosition, level);
    }

    @Inject(method = "renderSporeParticles", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sporePerformance$batch(RenderGuiEvent.Pre event, Vec3 color, int width, int height,
                                               float time, float weight, CallbackInfo callback) {
        if (!PerformanceConfig.CLIENT_SONA_BATCH_OVERLAY_QUADS.get()) return;
        SonaSporeOverlayBatch.render(event, color, width, height, time, weight);
        callback.cancel();
    }
}
