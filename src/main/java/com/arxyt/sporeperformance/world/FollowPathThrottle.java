package com.arxyt.sporeperformance.world;

import java.util.UUID;

/**
 * Per-goal path timing state. It deliberately contains no entity references, so a stopped goal
 * cannot keep a partner or level alive.
 */
public final class FollowPathThrottle {
    private UUID partnerId;
    private double targetX;
    private double targetY;
    private double targetZ;
    private boolean hasTargetPosition;
    private long regularNextAttempt = Long.MIN_VALUE;
    private long failureNextAttempt = Long.MIN_VALUE;
    private int failures;

    public boolean shouldAttempt(UUID candidateId, double x, double y, double z, long now, boolean pathActive,
                                 boolean reuseEnabled, int interval, double moveThreshold,
                                 boolean backoffEnabled) {
        boolean changed = !candidateId.equals(partnerId);
        double thresholdSqr = moveThreshold * moveThreshold;
        boolean moved = hasTargetPosition && distanceSqr(x, y, z) > thresholdSqr;
        if (changed || moved) {
            partnerId = candidateId;
            failures = 0;
            regularNextAttempt = Long.MIN_VALUE;
            failureNextAttempt = Long.MIN_VALUE;
            return true;
        }
        if (backoffEnabled && now < failureNextAttempt) return false;
        return !reuseEnabled || !pathActive || now >= regularNextAttempt;
    }

    public void recordAttempt(UUID candidateId, double x, double y, double z, long now, boolean success,
                              int interval, int phase, boolean backoffEnabled, int maximumBackoff) {
        partnerId = candidateId;
        targetX = x;
        targetY = y;
        targetZ = z;
        hasTargetPosition = true;
        if (success) {
            failures = 0;
            failureNextAttempt = Long.MIN_VALUE;
            regularNextAttempt = now + interval + phase;
            return;
        }
        regularNextAttempt = now;
        if (!backoffEnabled) return;
        failures = Math.min(failures + 1, 30);
        failureNextAttempt = now + backoffTicks(failures, maximumBackoff);
    }

    public void reset() {
        partnerId = null;
        hasTargetPosition = false;
        regularNextAttempt = Long.MIN_VALUE;
        failureNextAttempt = Long.MIN_VALUE;
        failures = 0;
    }

    public static int phase(UUID id) {
        return Math.floorMod(id.hashCode(), 20);
    }

    public static int backoffTicks(int failures, int maximum) {
        int shift = Math.min(Math.max(failures - 1, 0), 4);
        return Math.min(20 << shift, maximum);
    }

    private double distanceSqr(double x, double y, double z) {
        double dx = x - targetX;
        double dy = y - targetY;
        double dz = z - targetZ;
        return dx * dx + dy * dy + dz * dz;
    }
}
