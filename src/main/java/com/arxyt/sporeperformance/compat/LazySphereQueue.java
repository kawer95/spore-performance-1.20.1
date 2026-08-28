package com.arxyt.sporeperformance.compat;

import net.minecraft.core.BlockPos;

import java.util.AbstractList;
import java.util.RandomAccess;

/**
 * Constant-memory, sequentially efficient sphere view for sporesrp's mining API.
 *
 * <p>The original handler asks the list for monotonically increasing indexes and persists
 * the current index.  Keeping the current coordinate cursor makes that access O(1)
 * amortised without retaining hundreds of thousands of {@link BlockPos} instances.  Random
 * backwards access is deliberately supported as a recovery path, but is not a hot path.</p>
 */
public final class LazySphereQueue extends AbstractList<BlockPos> implements RandomAccess {
    private final BlockPos center;
    private final int radius;
    private final int radiusSquared;
    private final int size;
    private int cursorIndex = -1;
    private int cursorX;
    private int cursorY;
    private int cursorZ;

    public LazySphereQueue(BlockPos center, int radius) {
        this.center = center.immutable();
        this.radius = Math.max(0, radius);
        this.radiusSquared = this.radius * this.radius;
        this.size = count(this.radius, this.radiusSquared);
        resetCursor();
    }

    @Override
    public BlockPos get(int index) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException(index);
        if (index <= cursorIndex) resetCursor();
        while (cursorIndex < index) advance();
        return new BlockPos(center.getX() + cursorX, center.getY() + cursorY, center.getZ() + cursorZ);
    }

    @Override
    public int size() { return size; }

    private void resetCursor() {
        cursorIndex = -1;
        cursorX = -radius;
        cursorY = -radius;
        cursorZ = -radius - 1;
    }

    private void advance() {
        do {
            ++cursorZ;
            if (cursorZ > radius) {
                cursorZ = -radius;
                ++cursorY;
                if (cursorY > radius) {
                    cursorY = -radius;
                    ++cursorX;
                }
            }
        } while (cursorX <= radius && squared(cursorX, cursorY, cursorZ) > radiusSquared);
        ++cursorIndex;
    }

    private static int count(int radius, int radiusSquared) {
        int total = 0;
        for (int x = -radius; x <= radius; ++x) {
            for (int y = -radius; y <= radius; ++y) {
                int remaining = radiusSquared - x * x - y * y;
                if (remaining < 0) continue;
                total += 2 * (int) Math.sqrt(remaining) + 1;
            }
        }
        return total;
    }

    private static int squared(int x, int y, int z) { return x * x + y * y + z * z; }
}
