package com.arxyt.sporeperformance.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Guards the installed Spore 2.2.0j texture contract used by injected Howitzer rendering. */
class SporeLayerTexturesTest {
    @Test
    void howitzerTexturesAreAlwaysPresentAndMatchInstalledSporeContract() {
        var normal = SporeLayerTextures.howitzer(false);
        var radioactive = SporeLayerTextures.howitzer(true);

        assertNotNull(normal);
        assertNotNull(radioactive);
        assertEquals("spore:textures/entity/eyes/howitzer.png", normal.toString());
        assertEquals("spore:textures/entity/eyes/howitzer_radiation_glow.png", radioactive.toString());
    }
}
