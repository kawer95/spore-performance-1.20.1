package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.arxyt.sporeperformance.compat.TaczDamageBypass;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the selected TACZ weapons opt out of Spore's Calamity damage cap.
 *
 * The original method is kept intact.  Only the cap reads are redirected while
 * a source-aware, nested-safe context is open, so all other damage sources and
 * all of Calamity's side effects keep their normal order and values.
 */
@Mixin(value = Calamity.class, remap = false)
abstract class CalamityDamageBypassMixin {
    @Inject(method = "m_6469_", at = @At("HEAD"))
    private void sporeperformance$begin(DamageSource source, float amount,
                                         CallbackInfoReturnable<Boolean> callback) {
        TaczDamageBypass.beginCalamityDamage((Calamity) (Object) this, source);
    }

    @Inject(method = "m_6469_", at = @At("RETURN"))
    private void sporeperformance$end(DamageSource source, float amount,
                                       CallbackInfoReturnable<Boolean> callback) {
        TaczDamageBypass.endCalamityDamage((Calamity) (Object) this);
    }

    @Redirect(method = "m_6469_", at = @At(value = "INVOKE",
            target = "Lcom/Harbinger/Spore/Sentities/BaseEntities/Calamity;getDamageCap()D",
            remap = false))
    private double sporeperformance$disableCapForSelectedGun(Calamity calamity) {
        return TaczDamageBypass.bypassCurrentCalamityCap(calamity)
                ? 0.0D : calamity.getDamageCap();
    }
}
