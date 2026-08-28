package com.arxyt.sporeperformance.client.render;

/** Client-side change counter mixed into SynchedEntityData for animation cache invalidation. */
public interface SynchedDataVersion {
    long sporePerformance$dataVersion();
}
