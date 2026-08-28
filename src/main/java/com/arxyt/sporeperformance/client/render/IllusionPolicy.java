package com.arxyt.sporeperformance.client.render;

/** Pure policy kept independent from Spore classes so its boundary is unit-testable. */
public final class IllusionPolicy {
    public static boolean required(int madnessAmplifier, double distanceSquared) {
        return madnessAmplifier > 0 && distanceSquared > 900.0D;
    }

    private IllusionPolicy() {}
}
