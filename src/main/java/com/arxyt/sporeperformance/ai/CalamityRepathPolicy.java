package com.arxyt.sporeperformance.ai;

import java.util.UUID;

/**
 * Pure decision policy for calamity entity-target path requests.
 *
 * <p>The runtime owns Minecraft objects; this policy only decides whether an already active
 * route, direct-approach lease, or recent failed request can satisfy another Goal request.
 * Keeping the decision pure makes same-tick de-duplication and urgent invalidation testable
 * without loading Forge.</p>
 */
public final class CalamityRepathPolicy {
    public enum Decision {
        ALLOW,
        REUSE_ACTIVE_PATH,
        REUSE_DIRECT_APPROACH,
        SUPPRESS_FAILED_RETRY,
        SUPPRESS_SAME_TICK
    }

    public record Input(long now, long lastAttemptTick, long nextAttemptTick,
                        boolean sameTarget, double targetMovementSqr, double movementThresholdSqr,
                        boolean activePath, boolean directApproach, boolean previousAttemptFailed,
                        boolean urgentInvalidation) {}

    public static Decision decide(Input input) {
        if (input.sameTarget() && input.lastAttemptTick() == input.now()) {
            return Decision.SUPPRESS_SAME_TICK;
        }
        if (input.urgentInvalidation() || !input.sameTarget()
                || input.targetMovementSqr() > input.movementThresholdSqr()) {
            return Decision.ALLOW;
        }
        if (input.now() >= input.nextAttemptTick()) return Decision.ALLOW;
        if (input.activePath()) return Decision.REUSE_ACTIVE_PATH;
        if (input.directApproach()) return Decision.REUSE_DIRECT_APPROACH;
        if (input.previousAttemptFailed()) return Decision.SUPPRESS_FAILED_RETRY;
        return Decision.ALLOW;
    }

    /** Stable per-entity jitter spreads expensive path refreshes across 10-20 tick windows. */
    public static int jitteredInterval(UUID entityId, int minimum, int maximum) {
        int low = Math.max(1, Math.min(minimum, maximum));
        int high = Math.max(low, Math.max(minimum, maximum));
        int width = high - low + 1;
        long mixed = entityId.getMostSignificantBits() ^ Long.rotateLeft(entityId.getLeastSignificantBits(), 23);
        mixed ^= mixed >>> 33;
        mixed *= 0xff51afd7ed558ccdl;
        mixed ^= mixed >>> 33;
        return low + Math.floorMod((int) mixed, width);
    }

    private CalamityRepathPolicy() {}
}
