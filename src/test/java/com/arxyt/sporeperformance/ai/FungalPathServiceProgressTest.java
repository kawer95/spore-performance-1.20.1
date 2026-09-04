package com.arxyt.sporeperformance.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FungalPathServiceProgressTest {
    @Test
    void cachedPathResumesNearCurrentPositionInsteadOfOldStart() {
        Path path = new Path(List.of(
                new Node(0, 64, 0),
                new Node(1, 64, 0),
                new Node(2, 64, 0),
                new Node(3, 64, 0),
                new Node(4, 64, 0)
        ), new BlockPos(8, 64, 0), true);

        assertEquals(3, FungalPathService.resumeIndex(path, 3.45D, 64.0D, 0.5D));
    }

    @Test
    void cachedPathDoesNotScanUnboundedRouteCrossings() {
        List<Node> nodes = new java.util.ArrayList<>();
        for (int index = 0; index < 12; ++index) nodes.add(new Node(index, 64, 0));
        nodes.set(10, new Node(0, 64, 0));
        Path path = new Path(nodes, new BlockPos(12, 64, 0), true);

        assertEquals(0, FungalPathService.resumeIndex(path, 0.5D, 64.0D, 0.5D));
    }
}
