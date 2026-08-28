package com.arxyt.sporeperformance.runtime;

import net.minecraft.world.phys.Vec3;

/**
 * Runtime state deliberately kept outside the mixin package.
 *
 * <p>Mixin-generated target classes may reference this type in their field and
 * method descriptors. Mixin's class loader rejects direct references to
 * ordinary classes located under a declared mixin package.</p>
 */
public final class CalamityPathBackoff {
    public long nextAttempt;
    public int failures;
    public Vec3 targetPosition;
}
