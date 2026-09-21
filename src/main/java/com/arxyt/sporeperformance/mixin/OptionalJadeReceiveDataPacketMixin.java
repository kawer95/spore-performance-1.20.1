package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.SporePerformance;
import com.arxyt.sporeperformance.compat.NbtPayloadSanitizer;
import com.arxyt.sporeperformance.compat.CorruptEntityNbtGuard;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import snownee.jade.network.ReceiveDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/** Bounds Jade's server-side entity response before Forge allocates its network buffer. */
@Mixin(targets = "snownee.jade.network.ReceiveDataPacket", remap = false)
public abstract class OptionalJadeReceiveDataPacketMixin {
    private static final AtomicBoolean SPOREPERFORMANCE$WARNED = new AtomicBoolean();
    @Shadow public CompoundTag tag;

    @Inject(method = "write", at = @At("HEAD"), remap = false)
    private static void sporeperformance$boundEntityNbt(ReceiveDataPacket packet, FriendlyByteBuf buffer,
                                                        CallbackInfo callback) {
        OptionalJadeReceiveDataPacketMixin self = (OptionalJadeReceiveDataPacketMixin) (Object) packet;
        NbtPayloadSanitizer.Result result = NbtPayloadSanitizer.sanitize(self.tag,
                CorruptEntityNbtGuard.jadePayloadLimit());
        self.tag = result.tag();
        if (result.truncated() && SPOREPERFORMANCE$WARNED.compareAndSet(false, true)) {
            SporePerformance.LOGGER.warn("Jade entity payload exceeded the safe NBT budget; sent an empty response (reason={})",
                    result.reason());
        }
    }
}
