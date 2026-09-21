package com.arxyt.sporeperformance.compat;

import com.Harbinger.Spore.Core.Seffects;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Repairs already-loaded Marker instances and the canonical empty stack before they can spread. */
public final class CorruptEntityNbtGuard {
    public static final CorruptEntityNbtGuard INSTANCE = new CorruptEntityNbtGuard();

    private CorruptEntityNbtGuard() {}

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (emptyStackProtectionEnabled() && ItemStack.EMPTY.hasTag()) {
            ItemStack.EMPTY.setTag(null);
        }
        if (!markerSanitizerEnabled()
                || !(event.getEntity() instanceof LivingEntity living)) return;
        MobEffectInstance marker = living.getEffect(Seffects.MARKER.get());
        if (marker != null && !marker.getCurativeItems().isEmpty()) marker.getCurativeItems().clear();
    }

    public static boolean emptyStackProtectionEnabled() {
        try { return PerformanceConfig.SAFE_SANITIZE_CORRUPT_EMPTY_STACKS.get(); }
        catch (RuntimeException ignored) { return true; }
    }

    public static boolean markerSanitizerEnabled() {
        try { return PerformanceConfig.SAFE_SANITIZE_MARKER_CURATIVES.get(); }
        catch (RuntimeException ignored) { return true; }
    }

    public static int jadePayloadLimit() {
        try { return PerformanceConfig.SAFE_JADE_ENTITY_PAYLOAD_LIMIT_BYTES.get(); }
        catch (RuntimeException ignored) { return 524288; }
    }
}
