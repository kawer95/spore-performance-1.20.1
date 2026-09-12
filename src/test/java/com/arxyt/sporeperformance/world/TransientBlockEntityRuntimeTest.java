package com.arxyt.sporeperformance.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TransientBlockEntityRuntimeTest {
    @Test
    void classifiesKnownSporeTerrainThrowersWithoutReflection() {
        assertEquals(TransientBlockEntityRuntime.FallingSource.FOLIAGE,
                TransientBlockEntityRuntime.sourceForClass("com.Harbinger.Spore.Sentities.FoliageSpread"));
        assertEquals(TransientBlockEntityRuntime.FallingSource.HOHLFRESSER,
                TransientBlockEntityRuntime.sourceForClass("com.Harbinger.Spore.Sentities.Calamities.Hohlfresser"));
        assertEquals(TransientBlockEntityRuntime.FallingSource.THROWN_BLOCK,
                TransientBlockEntityRuntime.sourceForClass("com.Harbinger.Spore.Sentities.Projectile.ThrownBlockProjectile"));
        assertEquals(TransientBlockEntityRuntime.FallingSource.CORPSE,
                TransientBlockEntityRuntime.sourceForClass("com.Harbinger.Spore.Sentities.Utility.CorpseEntity"));
        assertNull(TransientBlockEntityRuntime.sourceForClass("net.minecraft.world.entity.item.FallingBlockEntity"));
    }
}
