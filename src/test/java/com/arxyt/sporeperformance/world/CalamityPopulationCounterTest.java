package com.arxyt.sporeperformance.world;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CalamityPopulationCounterTest {
    @Test
    void defaultNegativeLimitsDoNotRejectAndStillExposeCounts() {
        CalamityPopulationCounter<String> counter = new CalamityPopulationCounter<>();
        CalamityPopulationCounter.Limits unlimited = new CalamityPopulationCounter.Limits(-1, -1);

        assertEquals(CalamityPopulationCounter.Rejection.NONE,
                counter.track("overworld", UUID.randomUUID(), "spore:sieger", unlimited, true));
        assertEquals(CalamityPopulationCounter.Rejection.NONE,
                counter.track("overworld", UUID.randomUUID(), "spore:sieger", unlimited, true));
        assertEquals(2, counter.snapshot("overworld").total());
        assertEquals(2, counter.snapshot("overworld").perType().get("spore:sieger"));
    }

    @Test
    void totalAndPerTypeLimitsAreIndependent() {
        CalamityPopulationCounter<String> counter = new CalamityPopulationCounter<>();
        CalamityPopulationCounter.Limits limits = new CalamityPopulationCounter.Limits(3, 2);

        assertEquals(CalamityPopulationCounter.Rejection.NONE,
                counter.track("overworld", UUID.randomUUID(), "spore:sieger", limits, true));
        assertEquals(CalamityPopulationCounter.Rejection.NONE,
                counter.track("overworld", UUID.randomUUID(), "spore:sieger", limits, true));
        assertEquals(CalamityPopulationCounter.Rejection.TYPE,
                counter.track("overworld", UUID.randomUUID(), "spore:sieger", limits, true));
        assertEquals(CalamityPopulationCounter.Rejection.NONE,
                counter.track("overworld", UUID.randomUUID(), "spore:stahl", limits, true));
        assertEquals(CalamityPopulationCounter.Rejection.TOTAL,
                counter.track("overworld", UUID.randomUUID(), "spore:howitzer", limits, true));
    }

    @Test
    void savedEntitiesCanExceedAEnabledLimitButBlockFutureSpawns() {
        CalamityPopulationCounter<String> counter = new CalamityPopulationCounter<>();
        CalamityPopulationCounter.Limits limits = new CalamityPopulationCounter.Limits(1, 1);

        assertEquals(CalamityPopulationCounter.Rejection.NONE,
                counter.track("overworld", UUID.randomUUID(), "spore:sieger", limits, false));
        assertEquals(CalamityPopulationCounter.Rejection.NONE,
                counter.track("overworld", UUID.randomUUID(), "spore:sieger", limits, false));
        assertEquals(CalamityPopulationCounter.Rejection.TOTAL,
                counter.track("overworld", UUID.randomUUID(), "spore:stahl", limits, true));
        assertEquals(2, counter.snapshot("overworld").total());
    }

    @Test
    void leavingOneEntityFreesOnlyItsTypeSlot() {
        CalamityPopulationCounter<String> counter = new CalamityPopulationCounter<>();
        CalamityPopulationCounter.Limits limits = new CalamityPopulationCounter.Limits(-1, 1);
        UUID sieger = UUID.randomUUID();
        UUID stahl = UUID.randomUUID();

        assertEquals(CalamityPopulationCounter.Rejection.NONE,
                counter.track("overworld", sieger, "spore:sieger", limits, true));
        assertEquals(CalamityPopulationCounter.Rejection.NONE,
                counter.track("overworld", stahl, "spore:stahl", limits, true));
        assertEquals(CalamityPopulationCounter.Rejection.TYPE,
                counter.track("overworld", UUID.randomUUID(), "spore:sieger", limits, true));
        counter.untrack(sieger);
        assertEquals(CalamityPopulationCounter.Rejection.NONE,
                counter.track("overworld", UUID.randomUUID(), "spore:sieger", limits, true));
    }
}
