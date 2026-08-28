package com.arxyt.sporeperformance.world;

import com.Harbinger.Spore.Core.SConfig;
import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Compiles Spore's string block mapping only at startup/reload, never inside a foliage scan. */
@Mod.EventBusSubscriber(modid = SporePerformance.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class InfectionConversionCache {
    private static volatile Map<Block, BlockState> mappings = Map.of();
    /** The cache remains disabled until Forge has finished binding the common spec. */
    private static volatile boolean compiledMapEnabled;

    @SubscribeEvent
    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == PerformanceConfig.COMMON_SPEC) refresh();
    }

    @SubscribeEvent
    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == PerformanceConfig.COMMON_SPEC) refresh();
    }

    public static void refresh() {
        final boolean enabled;
        try {
            // Forge dispatches the Loading event before ConfigValue#get is bound in
            // some client/dev launch paths. Keep the old snapshot until the next
            // server lifecycle callback instead of crashing the loading overlay.
            enabled = PerformanceConfig.SAFE_COMPILED_INFECTION_MAP.get();
        } catch (IllegalStateException notBoundYet) {
            return;
        }
        if (!enabled) {
            mappings = Map.of();
            compiledMapEnabled = false;
            return;
        }
        Map<Block, BlockState> compiled = new HashMap<>();
        List<? extends String> raw;
        try {
            raw = SConfig.DATAGEN.block_infection.get();
        } catch (IllegalStateException unavailableDuringEarlyLoad) {
            return;
        } catch (RuntimeException malformedSporeConfig) {
            SporePerformance.LOGGER.debug("Spore infection mapping is unavailable; retaining the previous compiled snapshot", malformedSporeConfig);
            return;
        }
        for (String rule : raw) {
            String[] pair = rule.split("\\|", -1);
            if (pair.length != 2) {
                SporePerformance.LOGGER.warn("Ignoring malformed Spore infection mapping: {}", rule);
                continue;
            }
            Block from = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(pair[0]));
            Block to = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(pair[1]));
            if (from != null && to != null) compiled.put(from, to.defaultBlockState());
        }
        mappings = Map.copyOf(compiled);
        compiledMapEnabled = true;
    }

    public static boolean convert(Level level, BlockState source, BlockPos pos) {
        // Do not read Forge ConfigValue on the foliage hot path. Before the
        // first successful refresh, returning false preserves Spore's original
        // conversion path and avoids an early-load exception.
        if (!compiledMapEnabled) return false;
        BlockState target = mappings.get(source.getBlock());
        if (target == null) return true;
        level.setBlock(pos, target, 3);
        PerformanceMetrics.increment("foliage.compiled_conversion");
        return true;
    }

    private InfectionConversionCache() {}
}
