package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.client.render.ClientRenderMetrics;
import com.arxyt.sporeperformance.client.render.SonaInfectionFrameCache;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sona 1.5.1 copies the Oculus main depth attachment into a vanilla
 * {@link TextureTarget} before its infection post pass. Oculus uses a
 * different depth internal format, so the framebuffer blit fails every frame.
 * The post shader only samples that depth; it can safely sample the original
 * main-target depth texture directly and avoid the redundant incompatible copy.
 */
@Pseudo
@Mixin(targets = "com.scarasol.sona.client.renderer.InfectionShaderPostRenderer", remap = false)
public abstract class OptionalSonaInfectionShaderPostMixin {
    @Redirect(method = "onRenderShaderPost", at = @At(value = "INVOKE",
            target = "Lcom/scarasol/sona/manager/InfectionManager;canChunkInfection(Lnet/minecraft/world/level/Level;)Z"),
            remap = false)
    private static boolean sporePerformance$shareCanChunk(Level level) {
        return SonaInfectionFrameCache.canChunk(level);
    }

    @Redirect(method = "onRenderShaderPost", at = @At(value = "INVOKE",
            target = "Lcom/scarasol/sona/manager/InfectionManager;getAveZoneInfectionInRender(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/phys/Vec3;)D"),
            remap = false)
    private static double sporePerformance$shareAverage(Level level, Vec3 cameraPosition) {
        return SonaInfectionFrameCache.average(level, cameraPosition);
    }

    @Redirect(method = "getCurrentPostColor", at = @At(value = "INVOKE",
            target = "Lcom/scarasol/sona/manager/InfectionManager;getInfectionChunkFogColor(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/multiplayer/ClientLevel;)Lnet/minecraft/world/phys/Vec3;"),
            remap = false)
    private static Vec3 sporePerformance$shareColor(Vec3 base, Vec3 cameraPosition, ClientLevel level) {
        return SonaInfectionFrameCache.fogColor(base, cameraPosition, level);
    }

    @ModifyArgs(method = "onRenderShaderPost", at = @At(value = "INVOKE",
            target = "Lcom/scarasol/sona/client/renderer/InfectionShaderPostRenderer;ensurePostTarget(II)V"),
            remap = false)
    private static void sporePerformance$scalePostTarget(Args args) {
        if (!PerformanceConfig.CLIENT_SONA_POST_HALF_RESOLUTION.get()) return;
        args.set(0, Math.max(1, ((int) args.get(0)) / 2));
        args.set(1, Math.max(1, ((int) args.get(1)) / 2));
    }

    @Inject(method = "copyMainTarget", at = @At("HEAD"), cancellable = true, remap = false)
    private static void sporePerformance$downsampleColor(RenderTarget source, TextureTarget destination,
                                                          CallbackInfo callback) {
        if (!PerformanceConfig.CLIENT_SONA_POST_HALF_RESOLUTION.get()) return;
        GlStateManager._glBindFramebuffer(36008, source.frameBufferId);
        GlStateManager._glBindFramebuffer(36009, destination.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, source.width, source.height,
                0, 0, destination.width, destination.height, 16384, 9729);
        GlStateManager._glBindFramebuffer(36008, 0);
        GlStateManager._glBindFramebuffer(36009, 0);
        source.bindWrite(true);
        ClientRenderMetrics.increment("sona.post.half_resolution_frames");
        callback.cancel();
    }

    @Inject(method = "renderShaderPost", at = @At("HEAD"), remap = false)
    private static void sporePerformance$recordPostResolution(RenderTarget mainTarget,
                                                               TextureTarget postTarget,
                                                               ShaderInstance shader, Vec3 color,
                                                               float weight, float infection, float time,
                                                               CallbackInfo callback) {
        ClientRenderMetrics.increment(PerformanceConfig.CLIENT_SONA_POST_HALF_RESOLUTION.get()
                ? "sona.post.rendered_half" : "sona.post.rendered_full");
    }

    @Redirect(
            method = "copyMainTarget",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/pipeline/TextureTarget;m_83945_(Lcom/mojang/blaze3d/pipeline/RenderTarget;)V",
                    remap = false),
            require = 0,
            remap = false)
    private static void sporeperformance$skipIncompatibleDepthCopy(TextureTarget destination,
                                                                    RenderTarget source) {
        if (!PerformanceConfig.CLIENT_FIX_SONA_INFECTION_POST_DEPTH.get()) {
            destination.copyDepthFrom(source);
        }
    }

    @Redirect(
            method = "renderShaderPost",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/pipeline/TextureTarget;m_83980_()I",
                    remap = false),
            require = 0,
            remap = false)
    private static int sporeperformance$sampleMainDepthTexture(TextureTarget copiedTarget,
                                                                RenderTarget mainTarget,
                                                                TextureTarget ignoredPostTarget,
                                                                ShaderInstance ignoredShader,
                                                                Vec3 ignoredColor,
                                                                float ignoredWeight,
                                                                float ignoredInfection,
                                                                float ignoredTime) {
        return PerformanceConfig.CLIENT_FIX_SONA_INFECTION_POST_DEPTH.get()
                ? mainTarget.getDepthTextureId()
                : copiedTarget.getDepthTextureId();
    }
}
