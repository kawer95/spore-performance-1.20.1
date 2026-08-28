package com.arxyt.sporeperformance.world;

import java.util.UUID;

/** Internal bridge implemented by the ItemEntity mixin. */
public interface ManagedItemEntity {
    boolean sporeperformance$isPlayerDropped();
    void sporeperformance$setPlayerDropped(boolean value);
    boolean sporeperformance$isLifetimeConfigured();
    void sporeperformance$setLifetimeConfigured(boolean value);
    int sporeperformance$getAge();
    void sporeperformance$setAge(int age);
    int sporeperformance$getPickupDelay();
    void sporeperformance$setPickupDelay(int delay);
    UUID sporeperformance$getThrower();
    UUID sporeperformance$getTarget();
}
