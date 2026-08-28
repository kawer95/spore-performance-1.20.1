package com.arxyt.sporeperformance.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Startup-only Mixin eligibility ledger exposed by the status command. */
public final class MixinPatchStatus {
    private static final Map<String, OptionalCompatProbe.State> STATES = new ConcurrentHashMap<>();
    public static void record(String mixin, OptionalCompatProbe.State state) { STATES.put(mixin, state); }
    public static OptionalCompatProbe.State state(String mixin) { return STATES.getOrDefault(mixin, OptionalCompatProbe.State.SKIPPED); }
    private MixinPatchStatus() {}
}
