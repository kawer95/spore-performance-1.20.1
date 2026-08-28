package com.arxyt.sporeperformance.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SonaSporeOverlayBatchTest {
    @Test
    void preservesSonaParticleRangeAtDefaultScale() {
        assertEquals(16, SonaSporeOverlayBatch.particleCount(0.0F, true, 1.0));
        assertEquals(54, SonaSporeOverlayBatch.particleCount(1.0F, true, 1.0));
    }

    @Test
    void particleScaleIsIndependentAndBounded() {
        assertEquals(27, SonaSporeOverlayBatch.particleCount(1.0F, true, 0.5));
        assertEquals(54, SonaSporeOverlayBatch.particleCount(1.0F, false, 0.1));
        assertEquals(54, SonaSporeOverlayBatch.particleCount(3.0F, true, 1.0));
        assertEquals(0, SonaSporeOverlayBatch.particleCount(-3.0F, true, 1.0));
    }

    @Test
    void gpuCleanupNeverDeletesWithoutRenderThreadAndCurrentContext() {
        assertEquals(SonaSporeOverlayBatch.CleanupAction.DEFER,
                SonaSporeOverlayBatch.cleanupAction(false, true));
        assertEquals(SonaSporeOverlayBatch.CleanupAction.ABANDON,
                SonaSporeOverlayBatch.cleanupAction(true, false));
        assertEquals(SonaSporeOverlayBatch.CleanupAction.CLOSE,
                SonaSporeOverlayBatch.cleanupAction(true, true));
    }
}
