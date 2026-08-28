package com.arxyt.sporeperformance.registry;

import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.world.StahlRisingBlockEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class PerformanceEntities {
    public static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SporePerformance.MODID);
    public static final RegistryObject<EntityType<StahlRisingBlockEntity>> STAHL_RISING_BLOCK = TYPES.register(
            "stahl_rising_block", () -> EntityType.Builder.<StahlRisingBlockEntity>of(StahlRisingBlockEntity::new, MobCategory.MISC)
                    .sized(0.98F, 0.98F).clientTrackingRange(10).updateInterval(1)
                    .build(SporePerformance.MODID + ":stahl_rising_block"));

    private PerformanceEntities() {}
}
