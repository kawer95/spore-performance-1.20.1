package com.arxyt.sporeperformance.client.render;

import net.minecraft.resources.ResourceLocation;

/** Stable texture identifiers used by signature-gated Spore layer optimizations. */
public final class SporeLayerTextures {
    private static final ResourceLocation HOWITZER =
            new ResourceLocation("spore", "textures/entity/eyes/howitzer.png");
    private static final ResourceLocation HOWITZER_RADIOACTIVE =
            new ResourceLocation("spore", "textures/entity/eyes/howitzer_radiation_glow.png");

    public static ResourceLocation howitzer(boolean radioactive) {
        return radioactive ? HOWITZER_RADIOACTIVE : HOWITZER;
    }

    private SporeLayerTextures() {}
}
