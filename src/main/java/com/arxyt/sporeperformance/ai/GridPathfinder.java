package com.arxyt.sporeperformance.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** Pure worker-thread A* over an immutable passability/height snapshot. */
public final class GridPathfinder {
    public static List<Cell> find(Grid grid) {
        int size = grid.width * grid.depth;
        double[] costs = new double[size];
        int[] parents = new int[size];
        boolean[] closed = new boolean[size];
        Arrays.fill(costs, Double.POSITIVE_INFINITY);
        Arrays.fill(parents, -1);
        int start = grid.index(grid.startX, grid.startZ);
        int goal = grid.index(grid.goalX, grid.goalZ);
        if (start < 0 || goal < 0 || !grid.passable[start] || !grid.passable[goal]) return List.of();
        PriorityQueue<Open> open = new PriorityQueue<>(Comparator.comparingDouble(Open::score));
        costs[start] = 0.0D;
        open.add(new Open(start, heuristic(grid, start, goal)));
        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};
        while (!open.isEmpty()) {
            int current = open.poll().index;
            if (closed[current]) continue;
            if (current == goal) return reconstruct(grid, parents, goal);
            closed[current] = true;
            int x = current % grid.width;
            int z = current / grid.width;
            for (int direction = 0; direction < 4; direction++) {
                int nx = x + dx[direction];
                int nz = z + dz[direction];
                int next = grid.index(nx, nz);
                if (next < 0 || closed[next] || !grid.passable[next]) continue;
                int heightDelta = Math.abs(grid.heights[current] - grid.heights[next]);
                if (heightDelta > 1) continue;
                double candidate = costs[current] + 1.0D + heightDelta * 0.25D;
                if (candidate >= costs[next]) continue;
                costs[next] = candidate;
                parents[next] = current;
                open.add(new Open(next, candidate + heuristic(grid, next, goal)));
            }
        }
        return List.of();
    }

    private static double heuristic(Grid grid, int from, int goal) {
        return Math.abs(from % grid.width - goal % grid.width) + Math.abs(from / grid.width - goal / grid.width);
    }

    private static List<Cell> reconstruct(Grid grid, int[] parents, int goal) {
        List<Cell> reverse = new ArrayList<>();
        for (int cursor = goal; cursor >= 0; cursor = parents[cursor]) {
            int x = cursor % grid.width;
            int z = cursor / grid.width;
            reverse.add(new Cell(grid.minX + x, grid.heights[cursor], grid.minZ + z));
        }
        List<Cell> result = new ArrayList<>(reverse.size());
        for (int i = reverse.size() - 1; i >= 0; i--) result.add(reverse.get(i));
        return result;
    }

    public record Cell(int x, int y, int z) {}
    public record Grid(int minX, int minZ, int width, int depth, int startX, int startZ, int goalX, int goalZ,
                       boolean[] passable, int[] heights) {
        public Grid {
            if (width <= 0 || depth <= 0 || passable.length != width * depth || heights.length != width * depth)
                throw new IllegalArgumentException("Invalid immutable path grid");
            passable = passable.clone();
            heights = heights.clone();
        }
        int index(int x, int z) { return x < 0 || z < 0 || x >= width || z >= depth ? -1 : z * width + x; }
    }
    private record Open(int index, double score) {}
    private GridPathfinder() {}
}
