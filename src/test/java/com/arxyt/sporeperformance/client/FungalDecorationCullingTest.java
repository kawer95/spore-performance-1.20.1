package com.arxyt.sporeperformance.client;

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
}
