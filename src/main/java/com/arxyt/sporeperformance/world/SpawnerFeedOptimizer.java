package com.arxyt.sporeperformance.world;

import com.Harbinger.Spore.Core.SConfig;
import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Exact server replacement for the expensive block-position loop in Overgrown Spawner feed. */
public final class SpawnerFeedOptimizer {
    public static boolean feed(Level level, BlockPos position) {
        if (level.isClientSide) return true;
        if (!PerformanceConfig.SAFE_SPAWNER_SERVER_ONLY.get() || !(level instanceof ServerLevel server)) return false;

        int fullExtent = 2 * SConfig.DATAGEN.spawner_range.get();
        int radius = fullExtent / 2;
        // Deliberately retain Spore's raw BlockPos center and AABB dimensions.
        AABB area = AABB.ofSize(new Vec3(position.getX(), position.getY(), position.getZ()), fullExtent, fullExtent, fullExtent);
        List<LivingEntity> entities = server.getEntitiesOfClass(LivingEntity.class, area);
        for (var structure : StructureBlockIndex.INSTANCE.find(server, position, radius)) structure.addKills();
        for (Entity entity : entities) {
            if (entity instanceof Infected infected) {
                infected.setKills(infected.getKills() + 1);
                infected.setEvoPoints(infected.getEvoPoints() + 1);
            } else if (entity instanceof Calamity calamity) {
                calamity.setKills(calamity.getKills() + 1);
            }
        }
        PerformanceMetrics.increment("spawner.optimized_feed");
        return true;
    }

    private SpawnerFeedOptimizer() {}
}
