package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.BaseEntities.HohlMultipart;
import com.Harbinger.Spore.Sentities.Calamities.Hohlfresser;
import com.Harbinger.Spore.Sentities.HitboxesForParts;
import com.arxyt.sporeperformance.ai.HohlfresserMultipartPolicy;
import com.arxyt.sporeperformance.diagnostics.DebugTrace;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps Spore's Hohlfresser valid while a saved/loaded child UUID has not yet
 * resolved to its first multipart segment.  The native tick allocates a
 * null-filled array, synchronises adaptation, and only then creates segments;
 * an adapted Hohlfresser consequently dereferences a null element every 20
 * ticks.  Creating the native segment chain before that synchronisation keeps
 * the original eventual result and removes the invalid intermediate state.
 */
@Mixin(value = Hohlfresser.class, remap = false)
abstract class HohlfresserMultipartSafetyMixin {
    @Shadow private HohlMultipart[] parts;
    @Shadow public abstract Entity getChild();

    @Invoker("createSegments")
    abstract void sporeperformance$createSegments();

    @Inject(method = "m_8119_", at = @At(value = "INVOKE",
            target = "Lcom/Harbinger/Spore/Sentities/Calamities/Hohlfresser;rebuildPartsArray()V",
            shift = At.Shift.AFTER))
    private void sporeperformance$finishMissingChainBeforeAdaptation(CallbackInfo callback) {
        Hohlfresser self = (Hohlfresser) (Object) this;
        if (self.level().isClientSide || !HohlfresserMultipartPolicy.hasMissingSegments(parts)) return;

        Entity child = getChild();
        if (child instanceof HohlMultipart segment && segment.isAlive()) return;

        // This is the same native createSegments call that Spore makes later
        // in this tick; move it before the adaptation loop that dereferences
        // every array slot.
        sporeperformance$createSegments();
        PerformanceMetrics.increment("multipart.hohlfresser_chain_repaired");
        if (self.level() instanceof ServerLevel level) {
            DebugTrace.state(DebugTrace.Category.LIFECYCLE, level, DebugTrace.trace(self), self,
                    "hohl_chain_repaired", "reason=missing_or_unresolved_first_segment");
        }
    }

    @Redirect(method = "m_142687_", at = @At(value = "INVOKE",
            target = "Lcom/Harbinger/Spore/Sentities/BaseEntities/HohlMultipart;m_146870_()V", remap = false))
    private void sporeperformance$discardOnlyPresentSegments(HohlMultipart segment) {
        if (segment != null) {
            segment.discard();
            return;
        }
        PerformanceMetrics.increment("multipart.hohlfresser_missing_segment_remove_guard");
    }

    @Inject(method = "parts", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$excludeMissingSegmentsFromPartList(CallbackInfoReturnable<List<HitboxesForParts>> callback) {
        if (!HohlfresserMultipartPolicy.hasMissingSegments(parts)) return;
        Hohlfresser self = (Hohlfresser) (Object) this;
        boolean adapted = self.getAdaptation();
        List<HitboxesForParts> values = new ArrayList<>();
        values.add(HitboxesForParts.HOHL_JAW);
        values.add(HitboxesForParts.HOHL_HEAD);
        if (parts != null) {
            for (HohlMultipart segment : parts) {
                if (segment != null) values.add(sporeperformance$hitboxFor(segment, adapted));
            }
        }
        PerformanceMetrics.increment("multipart.hohlfresser_missing_segment_part_list_guard");
        callback.setReturnValue(values);
    }

    @Redirect(method = "summonCorpsePart", at = @At(value = "INVOKE",
            target = "Lcom/Harbinger/Spore/Sentities/Calamities/Hohlfresser;calculateSegmentsPosition(I)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 sporeperformance$safeCorpseSegmentPosition(Hohlfresser self, int index) {
        if (HohlfresserMultipartPolicy.validIndex(parts, index)) return parts[index].position();
        PerformanceMetrics.increment("multipart.hohlfresser_missing_segment_corpse_guard");
        return self.position();
    }

    private static HitboxesForParts sporeperformance$hitboxFor(HohlMultipart segment, boolean adapted) {
        if (segment.isTail()) return adapted ? HitboxesForParts.HOHL_ADA_TAIL : HitboxesForParts.HOHL_TAIL;
        if (segment.getSegmentVariant() == HohlMultipart.SegmentVariants.MELEE) {
            return adapted ? HitboxesForParts.HOHL_ADA_SEG2 : HitboxesForParts.HOHL_SEG2;
        }
        if (segment.getSegmentVariant() == HohlMultipart.SegmentVariants.ORGAN) {
            return adapted ? HitboxesForParts.HOHL_ADA_SEG3 : HitboxesForParts.HOHL_SEG3;
        }
        return adapted ? HitboxesForParts.HOHL_ADA_SEG1 : HitboxesForParts.HOHL_SEG1;
    }
}
