package com.arxyt.sporeperformance.client.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AcceleratedRenderingSignaturesTest {
    @Test
    void resolvesAndInvokesExactPublicStaticContract() throws Throwable {
        GoodCore.pushes = 0;
        AcceleratedRenderingSignatures.Handles handles = AcceleratedRenderingSignatures.resolve(GoodCore.class);
        assertTrue((boolean) handles.isLoaded().invokeExact());
        handles.forceTranslucent().invokeExact();
        handles.resetTranslucent().invokeExact();
        assertEquals(0, GoodCore.pushes);
    }

    @Test
    void rejectsMissingOrDriftedSignatures() {
        assertThrows(NoSuchMethodException.class, () -> AcceleratedRenderingSignatures.resolve(MissingReset.class));
        assertThrows(NoSuchMethodException.class, () -> AcceleratedRenderingSignatures.resolve(WrongReturn.class));
    }

    public static final class GoodCore {
        static int pushes;
        public static boolean isLoaded() { return true; }
        public static void forceEnableForceTranslucentAcceleration() { pushes++; }
        public static void resetForceTranslucentAcceleration() { pushes--; }
    }

    public static final class MissingReset {
        public static boolean isLoaded() { return true; }
        public static void forceEnableForceTranslucentAcceleration() {}
    }

    public static final class WrongReturn {
        public static int isLoaded() { return 1; }
        public static void forceEnableForceTranslucentAcceleration() {}
        public static void resetForceTranslucentAcceleration() {}
    }
}
