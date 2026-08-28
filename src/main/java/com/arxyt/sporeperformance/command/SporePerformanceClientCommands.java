package com.arxyt.sporeperformance.command;

import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.client.render.AcceleratedRenderingBridge;
import com.arxyt.sporeperformance.client.render.AnimationLodController;
import com.arxyt.sporeperformance.client.render.ClientRenderMetrics;
import com.arxyt.sporeperformance.client.render.IllusionRenderOptimizer;
import com.arxyt.sporeperformance.client.render.OpaqueModelPartRenderer;
import com.arxyt.sporeperformance.client.gui.OptimizationProfileScreen;
import com.arxyt.sporeperformance.config.OptimizationProfiles;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Read-only client diagnostics; configuration continues to be owned by the TOML file. */
@Mod.EventBusSubscriber(modid = SporePerformance.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SporePerformanceClientCommands {
    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("sporeperformanceclient")
                .then(Commands.literal("status").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(status()), false);
                    return 1;
                }))
                .then(Commands.literal("presets").executes(context -> {
                    OptimizationProfileScreen.open();
                    return 1;
                }))
                .then(Commands.literal("profile").executes(context -> {
                    OptimizationProfileScreen.open();
                    return 1;
                }))
                .then(Commands.literal("metrics")
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal("Spore 客户端渲染指标: " + ClientRenderMetrics.snapshot()), false);
                            return 1;
                        })
                        .then(Commands.literal("reset").executes(context -> {
                            ClientRenderMetrics.reset();
                            context.getSource().sendSuccess(() -> Component.literal("Spore 客户端渲染指标已重置。"), false);
                            return 1;
                        }))));
    }

    public static String status() {
        OptimizationProfiles.Profile profile = OptimizationProfiles.detectClientSafely();
        return "Spore 客户端渲染: preset=" + (profile == null ? "自定义" : profile.displayName())
                + ", acceleratedRendering=" + AcceleratedRenderingBridge.state()
                + ", animationLod=" + PerformanceConfig.CLIENT_ANIMATION_LOD.get()
                + ", majorAnimationLod=" + PerformanceConfig.CLIENT_MAJOR_ANIMATION_LOD.get()
                + ", calamityAnimationLod=" + PerformanceConfig.CLIENT_CALAMITY_ANIMATION_LOD.get()
                + ", organoidAnimationLod=" + PerformanceConfig.CLIENT_ORGANOID_ANIMATION_LOD.get()
                + ", hyperAnimationLod=" + PerformanceConfig.CLIENT_HYPER_ANIMATION_LOD.get()
                + ", protoAnimationLod=" + PerformanceConfig.CLIENT_PROTO_ANIMATION_LOD.get()
                + ", eyeMask=" + PerformanceConfig.CLIENT_EYE_OPAQUE_PART_MASK.get()
                + ", emissiveMask=" + PerformanceConfig.CLIENT_EMISSIVE_OPAQUE_PART_MASK.get()
                + ", multiRootMask=" + PerformanceConfig.CLIENT_VERIFIED_MULTI_ROOT_PART_MASK.get()
                + ", sonaBatch=" + PerformanceConfig.CLIENT_SONA_BATCH_OVERLAY_QUADS.get()
                + ", sonaOverlayLod=" + PerformanceConfig.CLIENT_SONA_OVERLAY_GEOMETRY_LOD.get()
                + ", sonaHalfPost=" + PerformanceConfig.CLIENT_SONA_POST_HALF_RESOLUTION.get()
                + ", illusionTypes=" + IllusionRenderOptimizer.cacheSize()
                + ", poses=" + AnimationLodController.cacheSize()
                + ", masks=" + OpaqueModelPartRenderer.planCount();
    }

    private SporePerformanceClientCommands() {}
}
