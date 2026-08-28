package com.arxyt.sporeperformance.compat;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LazySphereQueueTest {
    @Test
    void radiusTwoContainsExactlyTheIntegerSphereWithoutRetainingDuplicates() {
        BlockPos center = new BlockPos(10, 20, -30);
        LazySphereQueue queue = new LazySphereQueue(center, 2);
        assertEquals(33, queue.size());
        Set<BlockPos> positions = new HashSet<>();
        for (int index = 0; index < queue.size(); ++index) {
            BlockPos pos = queue.get(index);
            assertTrue(pos.distSqr(center) <= 4.0D);
            assertTrue(positions.add(pos));
        }
    }

    @Test
    void backwardsRecoveryReturnsTheSameCoordinateAfterASequentialAdvance() {
        LazySphereQueue queue = new LazySphereQueue(BlockPos.ZERO, 4);
        BlockPos expected = queue.get(11);
        queue.get(queue.size() - 1);
        assertEquals(expected, queue.get(11));
    }
}
