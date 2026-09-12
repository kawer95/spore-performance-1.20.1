package com.arxyt.sporeperformance.scheduler;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HowitzerOreScanCursorTest {
    @Test
    void visitsEveryCuboidCellExactlyOnceAcrossSlices() {
        HowitzerOreScanCursor cursor = new HowitzerOreScanCursor(-1, 5, 9, 1, 6, 10);
        Set<String> cells = new HashSet<>();
        int[] xyz = new int[3];
        while (cursor.hasNext()) {
            cursor.next(xyz);
            cells.add(xyz[0] + ":" + xyz[1] + ":" + xyz[2]);
        }
        assertEquals(12, cells.size());
        assertEquals(0, cursor.remaining());
        assertFalse(cursor.hasNext());
    }
}
