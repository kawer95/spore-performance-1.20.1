package com.arxyt.sporeperformance.ai;

/** Pure policy helpers for the calamity progress state machine; kept unit-testable without a Level. */
final class CalamityNavigationPolicy {
    static boolean hasProgress(int previousNode, int currentNode, double movedSqr, double thresholdSqr) {
        return currentNode >= 0 && currentNode != previousNode || movedSqr >= thresholdSqr;
    }

    /**
     * A calamity can move around a small circle while its path index never advances.  Raw
     * displacement is therefore not enough to prove that navigation made progress: the
     * distance to the current path node must establish a new, meaningful minimum.
     */
    static boolean improvesRouteDistance(double previousBest, double currentDistance) {
        return currentDistance + 0.025D < previousBest;
    }

    /**
     * A normal pivot is allowed to spend a few ticks aligning.  A route that both fails to get
     * closer and accumulates a quarter turn or more is instead circular steering.
     */
    static boolean isCircularSteering(long ticksWithoutRouteProgress, float accumulatedTurn) {
        return ticksWithoutRouteProgress >= 5L && accumulatedTurn >= 90.0F;
    }

    static boolean noProgress(long now, long lastProgress, int limitTicks) {
        return now - lastProgress >= limitTicks;
    }

    static int retryDelay(int failures, int baseTicks, int maxTicks) {
        int boundedFailures = Math.max(1, Math.min(3, failures));
        long delay = (long) baseTicks << (boundedFailures - 1);
        return (int) Math.min(maxTicks, Math.min(Integer.MAX_VALUE, delay));
    }

    /** Small per-tick displacement needed to distinguish genuine movement from position jitter. */
    static double progressThresholdSqr(double bodyWidth) {
        double threshold = Math.max(0.004D, Math.min(0.015D, bodyWidth * 0.001D));
        return threshold * threshold;
    }

    private CalamityNavigationPolicy() {}
}
