package com.arxyt.sporeperformance.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GridPathfinderTest {
    @Test
    void findsDetourWithoutAccessingWorldState() {
        int width = 5;
        int depth = 5;
        boolean[] passable = new boolean[width * depth];
        java.util.Arrays.fill(passable, true);
        passable[2] = false;
        passable[width + 2] = false;
        passable[width * 2 + 2] = false;
        int[] heights = new int[passable.length];
        List<GridPathfinder.Cell> path = GridPathfinder.find(new GridPathfinder.Grid(
                10, 20, width, depth, 0, 0, 4, 0, passable, heights));
        assertTrue(path.size() > 5);
        assertEquals(new GridPathfinder.Cell(10, 0, 20), path.get(0));
        assertEquals(new GridPathfinder.Cell(14, 0, 20), path.get(path.size() - 1));
    }

    @Test
    void rejectsUnreachableAndTwoBlockHeightJumps() {
        boolean[] passable = {true, true, true};
        int[] heights = {0, 2, 0};
        assertTrue(GridPathfinder.find(new GridPathfinder.Grid(0, 0, 3, 1,
                0, 0, 2, 0, passable, heights)).isEmpty());
    }

    @Test
    void snapshotDefensivelyCopiesArraysForWorkerSafety() {
        boolean[] passable = {true, true};
        int[] heights = {4, 4};
        GridPathfinder.Grid grid = new GridPathfinder.Grid(0, 0, 2, 1, 0, 0, 1, 0, passable, heights);
        passable[1] = false;
        heights[1] = 100;
        assertEquals(2, GridPathfinder.find(grid).size());
    }
}
