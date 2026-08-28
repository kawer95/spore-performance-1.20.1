package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.BaseEntities.Infected;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Uses AI Fix's existing NBT keys so worlds remain compatible with or without that optional mod. */
@Mixin(value = Infected.class, remap = false, priority = 900)
abstract class AiFixInfectedSearchPersistenceMixin {
    @Inject(method = "m_7380_", at = @At("TAIL"))
    private void sporeperformance$saveSearchOrder(CompoundTag tag, CallbackInfo callback) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get()) return;
        BlockPos pos = ((Infected) (Object) this).getSearchPos();
        tag.putBoolean("SporeFixHasSearchPos", pos != null);
        if (pos != null) {
            tag.putInt("SporeFixSearchX", pos.getX());
            tag.putInt("SporeFixSearchY", pos.getY());
            tag.putInt("SporeFixSearchZ", pos.getZ());
        }
    }

    @Inject(method = "m_7378_", at = @At("TAIL"))
    private void sporeperformance$loadSearchOrder(CompoundTag tag, CallbackInfo callback) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get()) return;
        Infected self = (Infected) (Object) this;
        self.setSearchPos(tag.getBoolean("SporeFixHasSearchPos")
                ? new BlockPos(tag.getInt("SporeFixSearchX"), tag.getInt("SporeFixSearchY"), tag.getInt("SporeFixSearchZ"))
                : null);
    }
}
