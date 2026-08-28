package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.BaseEntities.Calamity;
import com.Harbinger.Spore.Sentities.Calamities.Grakensenker;
import com.Harbinger.Spore.Sentities.Calamities.Hohlfresser;
import com.Harbinger.Spore.Sentities.Calamities.Sieger;
import com.arxyt.sporeperformance.compat.TaczDamageBypass;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes the selected TACZ weapons' reduction from Spore's own adapted
 * Calamity implementations.  The same source context is also used by the
 * base cap hook, while all non-whitelisted damage keeps the original branch.
 */
@Mixin(value = {Sieger.class, Hohlfresser.class, Grakensenker.class}, remap = false)
abstract class SporeAdaptationBypassMixin {
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
            target = "Lcom/Harbinger/Spore/Sentities/Calamities/Sieger;isAdapted()Z",
            remap = false), require = 0)
    private boolean sporeperformance$ignoreSiegerAdaptation(Sieger entity) {
        return TaczDamageBypass.bypassCurrentCalamityAdaptation(entity) ? false : entity.isAdapted();
    }

    @Redirect(method = "m_6469_", at = @At(value = "INVOKE",
            target = "Lcom/Harbinger/Spore/Sentities/Calamities/Hohlfresser;getAdaptation()Z",
            remap = false), require = 0)
    private boolean sporeperformance$ignoreHohlfresserAdaptation(Hohlfresser entity) {
        return TaczDamageBypass.bypassCurrentCalamityAdaptation(entity) ? false : entity.getAdaptation();
    }

    @Redirect(method = "m_6469_", at = @At(value = "INVOKE",
            target = "Lcom/Harbinger/Spore/Sentities/Calamities/Grakensenker;getAdaptation()Z",
            remap = false), require = 0)
    private boolean sporeperformance$ignoreGrakensenkerAdaptation(Grakensenker entity) {
        return TaczDamageBypass.bypassCurrentCalamityAdaptation(entity) ? false : entity.getAdaptation();
    }
}
