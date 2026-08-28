package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.compat.TaczDamageBypass;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips only sporesrp's adaptation handler for the configured TACZ gun IDs.
 * It does not cancel the Forge event, so other damage, armor, invulnerability,
 * resistance and death handlers still receive the hit normally.
 */
@Pseudo
@Mixin(targets = "com.maha_fish.sporesrp.adaptation.AdaptationEvents", remap = false)
abstract class OptionalSporeSrpDamageBypassMixin {
    @Inject(method = "onLivingHurt", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$skipAdaptation(LivingHurtEvent event, CallbackInfo callback) {
        DamageSource source = event.getSource();
        if (TaczDamageBypass.bypassSrpAdaptation(source)) callback.cancel();
    }
}
