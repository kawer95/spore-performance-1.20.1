package com.arxyt.sporeperformance.world;

import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Central, allocation-light classification for item merging and lifetimes. */
public final class ItemOptimizationPolicy {
    public static final TagKey<Item> FAST_DESPAWN = tag("fast_despawn_items");
    public static final TagKey<Item> NORMAL_DESPAWN = tag("normal_despawn_items");
    public static final TagKey<Item> LIFETIME_EXEMPT = tag("item_lifetime_exempt");
    public static final TagKey<Item> MERGE_EXEMPT = tag("item_merge_exempt");

    public static boolean managedNamespace(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key != null && (PerformanceConfig.ITEM_MERGE_GLOBAL.get() || "spore".equals(key.getNamespace()));
    }

    public static boolean managedForMerge(ItemEntity entity) {
        ItemStack stack = entity.getItem();
        return PerformanceConfig.ITEM_MERGE_ENABLED.get() && !stack.isEmpty()
                && managedNamespace(stack) && !stack.is(MERGE_EXEMPT);
    }

    public static void applyLifetime(ItemEntity entity) {
        if (!PerformanceConfig.ITEM_LIFETIME_ENABLED.get() || !(entity instanceof ManagedItemEntity managed)
                || managed.sporeperformance$isLifetimeConfigured()) return;
        managed.sporeperformance$setLifetimeConfigured(true);
        ItemStack stack = entity.getItem();
        if (stack.isEmpty() || !managedNamespace(stack) || stack.is(LIFETIME_EXEMPT)) return;

        int configured;
        if (managed.sporeperformance$isPlayerDropped()) {
            configured = PerformanceConfig.ITEM_LIFETIME_PLAYER.get();
        } else if (PerformanceConfig.ITEM_LIFETIME_PROTECT_SPECIAL.get() && isSpecial(stack)) {
            configured = 6000;
        } else if (isFast(stack)) {
            configured = PerformanceConfig.ITEM_LIFETIME_FAST.get();
        } else {
            configured = PerformanceConfig.ITEM_LIFETIME_NORMAL.get();
        }
        entity.lifespan = ItemOptimizationMath.shortenedLifetime(entity.lifespan, configured);
    }

    public static boolean isFast(ItemStack stack) {
        if (stack.is(FAST_DESPAWN)) return true;
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key == null || !"spore".equals(key.getNamespace())) return false;
        return switch (key.getPath()) {
            case "biomass", "fang", "tendons" -> true;
            default -> false;
        };
    }

    public static boolean isSpecial(ItemStack stack) {
        return stack.getMaxStackSize() <= 1 || stack.hasCustomHoverName() || stack.isEnchanted()
                || (stack.isDamageableItem() && stack.isDamaged())
                || (stack.hasTag() && stack.getTag() != null && !stack.getTag().isEmpty());
    }

    private static TagKey<Item> tag(String path) {
        return TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                new ResourceLocation(SporePerformance.MODID, path));
    }

    private ItemOptimizationPolicy() {}
}
