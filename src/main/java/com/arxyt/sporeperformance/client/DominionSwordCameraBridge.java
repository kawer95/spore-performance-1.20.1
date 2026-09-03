package com.arxyt.sporeperformance.client;

import com.arxyt.sporeperformance.SporePerformance;
import net.minecraftforge.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Optional bridge to Dominion Sword's detached command camera.
 *
 * <p>The performance addon deliberately does not compile against Dominion Sword.  The bridge is
 * resolved once on the client after the mod list is available, then the render culling hot path
 * only invokes the cached static handles.  A missing class or a changed signature disables this
 * integration without affecting ordinary player-centred culling.</p>
 */
public final class DominionSwordCameraBridge {
    private static final String MOD_ID = "dominionsword";
    private static final String CLIENT_SPIRIT_CLASS = "com.arxyt.dominionsword.client.ClientSpirit";
    private static final MethodType BOOLEAN_RETURN = MethodType.methodType(boolean.class);

    private static volatile boolean resolved;
    private static volatile MethodHandle activeHandle;
    private static volatile MethodHandle transitionHandle;
    private static volatile State state = State.UNRESOLVED;
    private static boolean failureLogged;

    public enum State {
        UNRESOLVED,
        ABSENT,
        ACTIVE,
        INCOMPATIBLE
    }

    /** Returns true while Dominion Sword owns the rendered view, including handoff frames. */
    public static boolean detachedCameraActive() {
        ensureResolved();
        MethodHandle active = activeHandle;
        MethodHandle transition = transitionHandle;
        if (active == null && transition == null) return false;
        try {
            if (active != null && (boolean) active.invokeExact()) return true;
            return transition != null && (boolean) transition.invokeExact();
        } catch (Throwable throwable) {
            disableAfterInvocationFailure(throwable);
            return false;
        }
    }

    public static State state() {
        ensureResolved();
        return state;
    }

    private static void ensureResolved() {
        if (resolved) return;
        synchronized (DominionSwordCameraBridge.class) {
            if (resolved) return;
            if (!ModList.get().isLoaded(MOD_ID)) {
                state = State.ABSENT;
                resolved = true;
                return;
            }
            try {
                Class<?> type = Class.forName(CLIENT_SPIRIT_CLASS, false,
                        DominionSwordCameraBridge.class.getClassLoader());
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                activeHandle = lookup.findStatic(type, "active", BOOLEAN_RETURN);
                try {
                    transitionHandle = lookup.findStatic(type, "detachedCameraTransition", BOOLEAN_RETURN);
                } catch (NoSuchMethodException | IllegalAccessException ignored) {
                    // Older Dominion Sword builds expose active() but not the transition helper.
                }
                state = State.ACTIVE;
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | LinkageError failure) {
                state = State.INCOMPATIBLE;
                logFailureOnce(failure);
            } finally {
                resolved = true;
            }
        }
    }

    private static void disableAfterInvocationFailure(Throwable failure) {
        activeHandle = null;
        transitionHandle = null;
        state = State.INCOMPATIBLE;
        logFailureOnce(failure);
    }

    private static synchronized void logFailureOnce(Throwable failure) {
        if (failureLogged) return;
        failureLogged = true;
        SporePerformance.LOGGER.warn("[DominionSwordCompat] command-camera bridge disabled; "
                + "fungal decoration culling will use the player viewpoint", failure);
    }

    private DominionSwordCameraBridge() {}
}
