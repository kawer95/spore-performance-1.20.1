package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.client.FungalDecorationCulling;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional Embeddium 0.3.31 chunk-meshing hook. It cancels only model emission;
 * the client world retains the real block state, collision, lighting and interactions.
 */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer", remap = false)
public abstract class OptionalEmbeddiumBlockRendererMixin {
    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true, remap = false)
    private void sporeperformance$cullDistantFungalDecoration(BlockRenderContext context,
                                                               ChunkBuildBuffers buffers,
                                                               CallbackInfo callback) {
        if (FungalDecorationCulling.shouldCull(context.state(), context.pos())) callback.cancel();
    }
}
