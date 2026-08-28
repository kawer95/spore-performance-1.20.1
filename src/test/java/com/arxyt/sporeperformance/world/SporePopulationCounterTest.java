package com.arxyt.sporeperformance.world;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SporePopulationCounterTest {
    private static final SporePopulationCounter.Category UNIT = new SporePopulationCounter.Category(true, false, false);
    private static final SporePopulationCounter.Category MOUND = new SporePopulationCounter.Category(true, true, false);
    private static final SporePopulationCounter.Category TENDRIL = new SporePopulationCounter.Category(true, false, true);

    @Test
    void appliesTheDefaultStyleTotalCapPerDimensionInConstantTime() {
        SporePopulationCounter<String> counter = new SporePopulationCounter<>();
        SporePopulationCounter.Limits limits = new SporePopulationCounter.Limits(200, 16, 32);
        for (int index = 0; index < 200; ++index) {
            assertEquals(SporePopulationCounter.Rejection.NONE, counter.track("overworld", UUID.randomUUID(), UNIT, limits, true));
        }
        assertEquals(SporePopulationCounter.Rejection.FUNGAL_UNITS, counter.track("overworld", UUID.randomUUID(), UNIT, limits, true));
        assertEquals(SporePopulationCounter.Rejection.NONE, counter.track("nether", UUID.randomUUID(), UNIT, limits, true));
        assertEquals(200, counter.snapshot("overworld").fungalUnits());
        assertEquals(1, counter.snapshot("nether").fungalUnits());
    }

    @Test
    void subtypeCapsRemainIndependentFromTheAggregateCap() {
        SporePopulationCounter<String> counter = new SporePopulationCounter<>();
        SporePopulationCounter.Limits limits = new SporePopulationCounter.Limits(200, 1, 1);
        assertEquals(SporePopulationCounter.Rejection.NONE, counter.track("overworld", UUID.randomUUID(), MOUND, limits, true));
        assertEquals(SporePopulationCounter.Rejection.MOUNDS, counter.track("overworld", UUID.randomUUID(), MOUND, limits, true));
        assertEquals(SporePopulationCounter.Rejection.NONE, counter.track("overworld", UUID.randomUUID(), TENDRIL, limits, true));
        assertEquals(SporePopulationCounter.Rejection.TENDRILS, counter.track("overworld", UUID.randomUUID(), TENDRIL, limits, true));
    }

    @Test
    void savedEntitiesCanReloadOverTheNewCapButStillConsumeFutureCapacity() {
        SporePopulationCounter<String> counter = new SporePopulationCounter<>();
        SporePopulationCounter.Limits limits = new SporePopulationCounter.Limits(1, 0, 0);
        assertEquals(SporePopulationCounter.Rejection.NONE, counter.track("overworld", UUID.randomUUID(), UNIT, limits, false));
        assertEquals(SporePopulationCounter.Rejection.NONE, counter.track("overworld", UUID.randomUUID(), UNIT, limits, false));
        assertEquals(SporePopulationCounter.Rejection.FUNGAL_UNITS, counter.track("overworld", UUID.randomUUID(), UNIT, limits, true));
        assertEquals(2, counter.snapshot("overworld").fungalUnits());
    }

    @Test
    void leavingEntityFreesItsExactCategoryCapacity() {
        SporePopulationCounter<String> counter = new SporePopulationCounter<>();
        SporePopulationCounter.Limits limits = new SporePopulationCounter.Limits(1, 1, 1);
        UUID tendril = UUID.randomUUID();
        assertEquals(SporePopulationCounter.Rejection.NONE, counter.track("overworld", tendril, TENDRIL, limits, true));
        counter.untrack(tendril);
        assertEquals(SporePopulationCounter.Rejection.NONE, counter.track("overworld", UUID.randomUUID(), TENDRIL, limits, true));
    }
}
