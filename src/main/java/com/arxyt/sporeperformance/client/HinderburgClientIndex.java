package com.arxyt.sporeperformance.client;

import com.Harbinger.Spore.Sentities.Calamities.Hinderburg;
import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client-only entity membership index used by AI Fix's two Hinderburg-only visual passes. */
@Mod.EventBusSubscriber(modid = SporePerformance.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HinderburgClientIndex {
    private static final Map<Integer, Entity> HINDERBURGS = new LinkedHashMap<>();

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide && event.getEntity() instanceof Hinderburg) HINDERBURGS.put(event.getEntity().getId(), event.getEntity());
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide) HINDERBURGS.remove(event.getEntity().getId());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        HINDERBURGS.clear();
    }

    public static Iterable<Entity> snapshot() {
        if (!PerformanceConfig.CLIENT_HINDERBURG_INDEX.get()) return List.of();
        HINDERBURGS.values().removeIf(entity -> entity.isRemoved() || !(entity instanceof Hinderburg));
        return new ArrayList<>(HINDERBURGS.values());
    }

    private HinderburgClientIndex() {}
}
