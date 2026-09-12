package com.arxyt.sporeperformance.scheduler;

/** Allocation-free x-fast cuboid cursor used by incremental Howitzer ore searches. */
public final class HowitzerOreScanCursor {
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    private final int total;
    private int index;

    public HowitzerOreScanCursor(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = Math.max(0, maxX - minX + 1);
        this.sizeY = Math.max(0, maxY - minY + 1);
        this.sizeZ = Math.max(0, maxZ - minZ + 1);
        long volume = (long) sizeX * sizeY * sizeZ;
        this.total = volume > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) volume;
    }

    public boolean hasNext() {
        return index < total;
    }

    /** Writes the next coordinate into {@code xyz[0..2]}. */
    public void next(int[] xyz) {
        if (!hasNext()) throw new IllegalStateException("Howitzer ore cursor exhausted");
        int current = index++;
        int x = current % sizeX;
        int yz = current / sizeX;
        int y = yz % sizeY;
        int z = yz / sizeY;
        xyz[0] = minX + x;
        xyz[1] = minY + y;
        xyz[2] = minZ + z;
    }

    public int remaining() {
        return total - index;
    }
}
