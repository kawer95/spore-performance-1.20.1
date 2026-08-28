package com.arxyt.sporeperformance.command;

import com.arxyt.sporeperformance.compat.OptionalCompatProbe;
import com.arxyt.sporeperformance.compat.TaczDamageBypass;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import com.arxyt.sporeperformance.world.SporePopulationLimiter;
import com.arxyt.sporeperformance.world.ItemMergeCoordinator;
import com.arxyt.sporeperformance.world.FungalWorkBudget;
import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import com.arxyt.sporeperformance.diagnostics.DebugTrace;
import com.arxyt.sporeperformance.diagnostics.CalamityTrace;
import com.arxyt.sporeperformance.diagnostics.LoadedEntityCensus;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.config.OptimizationProfiles;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/** Administrative inspection and preset commands. */
public final class SporePerformanceCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sporeperformance")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("status").executes(context -> {
                    OptionalCompatProbe.statusLines().forEach(line -> context.getSource().sendSuccess(() -> Component.literal(line), false));
                    OptimizationProfiles.Profile profile = OptimizationProfiles.detectCommonSafely();
                    context.getSource().sendSuccess(() -> Component.literal("Spore Performance preset="
                            + (profile == null ? "custom" : profile.displayName())), false);
                    TaczDamageBypass.statusLines().forEach(line -> context.getSource().sendSuccess(() -> Component.literal(line), false));
                    SporePopulationLimiter.INSTANCE.statusLines().forEach(line -> context.getSource().sendSuccess(() -> Component.literal(line), false));
                    ItemMergeCoordinator.INSTANCE.statusLines().forEach(line -> context.getSource().sendSuccess(() -> Component.literal(line), false));
                    FungalWorkBudget.INSTANCE.statusLines().forEach(line -> context.getSource().sendSuccess(() -> Component.literal(line), false));
                    FungalAiRuntime.INSTANCE.statusLines().forEach(line -> context.getSource().sendSuccess(() -> Component.literal(line), false));
                    return 1;
                }))
                .then(Commands.literal("profile")
                        .then(Commands.argument("preset", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        java.util.List.of("normal", "aggressive", "all"), builder))
                                .executes(context -> applyProfile(context.getSource(),
                                        StringArgumentType.getString(context, "preset")))))
                .then(Commands.literal("metrics")
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal("Spore Performance metrics: " + PerformanceMetrics.snapshot()), false);
                            return 1;
                        })
                        .then(Commands.literal("reset").executes(context -> {
                            PerformanceMetrics.reset();
                            context.getSource().sendSuccess(() -> Component.literal("Spore Performance metrics reset."), false);
                            return 1;
                        })))
                .then(Commands.literal("census")
                        .executes(context -> sendCensus(context.getSource(), null))
                        .then(Commands.literal("spore").executes(context -> sendCensus(context.getSource(), "spore"))))
                .then(Commands.literal("calamitytrace")
                        .then(Commands.literal("status").executes(context -> {
                            CalamityTrace.INSTANCE.statusLines().forEach(line -> context.getSource().sendSuccess(() -> Component.literal(line), false));
                            return 1;
                        }))
                        .then(Commands.literal("reset").executes(context -> {
                            CalamityTrace.INSTANCE.reset();
                            context.getSource().sendSuccess(() -> Component.literal("Calamity trace state reset."), false);
                            return 1;
                        })))
                .then(Commands.literal("debug")
                        .then(Commands.literal("status").executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal("Debug enabled=" + PerformanceConfig.DEBUG_ENABLED.get()
                                    + ", file=" + DebugTrace.file() + ", watched=" + DebugTrace.watched()
                                    + ", dropped=" + DebugTrace.dropped()), false);
                            return 1;
                        }))
                        .then(Commands.literal("recent")
                                .executes(context -> sendRecent(context.getSource(), 10))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 50))
                                        .executes(context -> sendRecent(context.getSource(), IntegerArgumentType.getInteger(context, "count")))))
                        .then(Commands.literal("watch").then(Commands.argument("uuid", StringArgumentType.word()).executes(context -> {
                            try {
                                java.util.UUID uuid = java.util.UUID.fromString(StringArgumentType.getString(context, "uuid"));
                                DebugTrace.watch(uuid);
                                context.getSource().sendSuccess(() -> Component.literal("Watching " + uuid), false);
                                return 1;
                            } catch (IllegalArgumentException exception) {
                                context.getSource().sendFailure(Component.literal("Invalid UUID"));
                                return 0;
                            }
                        })))
                        .then(Commands.literal("unwatch").then(Commands.argument("uuid", StringArgumentType.word()).executes(context -> {
                            try { DebugTrace.unwatch(java.util.UUID.fromString(StringArgumentType.getString(context, "uuid"))); return 1; }
                            catch (IllegalArgumentException exception) { context.getSource().sendFailure(Component.literal("Invalid UUID")); return 0; }
                        })))
                        .then(Commands.literal("clearwatch").executes(context -> { DebugTrace.clearWatch(); return 1; }))
                        .then(Commands.literal("reset").executes(context -> { DebugTrace.reset(); return 1; }))));
    }

    private static int sendRecent(CommandSourceStack source, int count) {
        DebugTrace.recent(count).forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return 1;
    }

    private static int sendCensus(CommandSourceStack source, String namespace) {
        LoadedEntityCensus.INSTANCE.statusLines(namespace)
                .forEach(line -> source.sendSuccess(() -> Component.literal(line), false));
        return 1;
    }

    private static int applyProfile(CommandSourceStack source, String rawProfile) {
        OptimizationProfiles.Profile profile = OptimizationProfiles.Profile.parse(rawProfile);
        if (profile == null) {
            source.sendFailure(Component.literal("未知预设。可用：normal、aggressive、all。"));
            return 0;
        }
        OptimizationProfiles.Result result = OptimizationProfiles.applyCommon(profile);
        source.sendSuccess(() -> Component.literal("Spore Performance：" + result.summary()), true);
        return 1;
    }

    private SporePerformanceCommands() {}
}
