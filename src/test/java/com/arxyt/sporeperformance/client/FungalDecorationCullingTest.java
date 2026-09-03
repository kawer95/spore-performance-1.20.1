package com.arxyt.sporeperformance.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies conservative section/boundary classification used to limit chunk rebuilds. */
final class FungalDecorationCullingTest {
    @Test
    void classifiesFullyVisibleSection() {
        assertEquals(-1, FungalDecorationCulling.sectionRelation(0, 0, 0, 8.0D, 8.0D, 8.0D, 32.0D));
    }

    @Test
    void classifiesFullyCulledSection() {
        assertEquals(1, FungalDecorationCulling.sectionRelation(3, 0, 0, 8.0D, 8.0D, 8.0D, 32.0D));
    }

    @Test
    void classifiesBoundarySection() {
        assertEquals(0, FungalDecorationCulling.sectionRelation(2, 0, 0, 8.0D, 8.0D, 8.0D, 32.0D));
    }

    @Test
    void classificationIsSymmetricAcrossNegativeCoordinates() {
        assertEquals(0, FungalDecorationCulling.sectionRelation(-3, 0, 0, -8.0D, 8.0D, 8.0D, 32.0D));
    }

    @Test
    void commandModeUsesTheRenderedCameraAsTheCullingCentre() {
        Vec3 playerEye = new Vec3(0.5D, 65.0D, 0.5D);
        Vec3 commandCamera = new Vec3(120.5D, 90.0D, -42.5D);
        assertEquals(commandCamera, FungalDecorationCulling.selectViewpoint(true, playerEye, commandCamera));
    }

    @Test
    void normalModeContinuesToFollowThePlayerEye() {
        Vec3 playerEye = new Vec3(0.5D, 65.0D, 0.5D);
        Vec3 otherCamera = new Vec3(120.5D, 90.0D, -42.5D);
        assertEquals(playerEye, FungalDecorationCulling.selectViewpoint(false, playerEye, otherCamera));
    }

    @Test
    void invalidCommandCameraFallsBackToThePlayerEye() {
        Vec3 playerEye = new Vec3(0.5D, 65.0D, 0.5D);
        Vec3 invalidCamera = new Vec3(Double.NaN, 90.0D, -42.5D);
        assertEquals(playerEye, FungalDecorationCulling.selectViewpoint(true, playerEye, invalidCamera));
    }
}
