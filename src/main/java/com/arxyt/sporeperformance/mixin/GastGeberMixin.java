package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.ExtremelySusThings.Utilities;
import com.Harbinger.Spore.Sentities.Utility.GastGeber;
import com.arxyt.sporeperformance.world.FungalWorkBudget;
import com.arxyt.sporeperformance.world.LivingEntitySpatialIndex;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GastGeber.class, remap = false)
abstract class GastGeberMixin {
    @Redirect(method = "m_8119_", at = @At(value = "INVOKE", target = "Lcom/Harbinger/Spore/Sentities/Utility/GastGeber;SpreadInfection(Lnet/minecraft/world/level/Level;DLnet/minecraft/core/BlockPos;)V", remap = false))
    private void sporeperformance$gateInfectionSpread(GastGeber self, net.minecraft.world.level.Level level,
                                                       double range, net.minecraft.core.BlockPos origin) {
        if (!level.isClientSide && !FungalWorkBudget.INSTANCE.mayWork(self, FungalWorkBudget.WorkKind.GASTGEBER)) return;
        self.SpreadInfection(level, range, origin);
    }

    /** @author ARXYT @reason Reuse the loaded-entity spatial index and enforce GastGeber work tokens. */
    @Overwrite
    public void SpreadEffect() {
        GastGeber self = (GastGeber) (Object) this;
        if (!(self.level() instanceof ServerLevel level)
                || !FungalWorkBudget.INSTANCE.mayWork(self, FungalWorkBudget.WorkKind.GASTGEBER)) return;
        var area = self.getBoundingBox().inflate(16.0D);
        for (LivingEntity living : LivingEntitySpatialIndex.INSTANCE.query(level, area, LivingEntity.class, self)) {
            if (self.TARGET_SELECTOR.test(living) && !Utilities.helmetList().contains(living.getItemBySlot(EquipmentSlot.HEAD).getItem())) {
                living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 0));
                living.addEffect(new MobEffectInstance(com.Harbinger.Spore.Core.Seffects.MYCELIUM.get(), 600, 1));
            }
        }
        double x = self.getX() - (self.getRandom().nextFloat() - 0.2F) * 0.2D;
        double y = self.getY() + (self.getRandom().nextFloat() - 0.5F) * 5.0D;
        double z = self.getZ() + (self.getRandom().nextFloat() - 0.2F) * 0.2D;
        level.sendParticles(com.Harbinger.Spore.Core.Sparticles.BLOOD_PARTICLE.get(), x, y, z, 12, 0, 0, 0, 1);
    }
}
