package com.arxyt.sporeperformance.client.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/** Strict public API signature resolver for the optional AcceleratedRendering bridge. */
public final class AcceleratedRenderingSignatures {
    public static Handles resolve(Class<?> core) throws NoSuchMethodException, IllegalAccessException {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        return new Handles(
                lookup.findStatic(core, "isLoaded", MethodType.methodType(boolean.class)),
                lookup.findStatic(core, "forceEnableForceTranslucentAcceleration", MethodType.methodType(void.class)),
                lookup.findStatic(core, "resetForceTranslucentAcceleration", MethodType.methodType(void.class)));
    }

    public record Handles(MethodHandle isLoaded, MethodHandle forceTranslucent, MethodHandle resetTranslucent) {}

    private AcceleratedRenderingSignatures() {}
}
