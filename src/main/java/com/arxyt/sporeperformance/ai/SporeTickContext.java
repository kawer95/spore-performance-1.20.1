package com.arxyt.sporeperformance.ai;

import net.minecraft.world.entity.Entity;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.function.Supplier;

/** Server-thread nesting context used to route generic entity queries made by Spore ticks. */
public final class SporeTickContext {
    private static final ThreadLocal<ArrayDeque<Entity>> STACK = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Integer> BYPASS = ThreadLocal.withInitial(() -> 0);

    public static void enter(Entity entity) { STACK.get().push(entity); }
    public static void exit() {
        ArrayDeque<Entity> stack = STACK.get();
        if (!stack.isEmpty()) stack.pop();
        if (stack.isEmpty()) STACK.remove();
    }
    @Nullable public static Entity current() {
        if (BYPASS.get() > 0) return null;
        ArrayDeque<Entity> stack = STACK.get();
        return stack.isEmpty() ? null : stack.peek();
    }
    public static <T> T withoutRouting(Supplier<T> supplier) {
        BYPASS.set(BYPASS.get() + 1);
        try { return supplier.get(); }
        finally {
            int depth = BYPASS.get() - 1;
            if (depth <= 0) BYPASS.remove(); else BYPASS.set(depth);
        }
    }
    private SporeTickContext() {}
}
