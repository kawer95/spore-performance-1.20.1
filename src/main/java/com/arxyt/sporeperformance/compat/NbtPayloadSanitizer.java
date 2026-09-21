package com.arxyt.sporeperformance.compat;

import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.nio.charset.StandardCharsets;

/** Bounded, non-mutating copy used before optional HUD mods serialize entity NBT. */
public final class NbtPayloadSanitizer {
    public static final int MAX_DEPTH = 32;
    public static final int MAX_CONTAINER_ENTRIES = 4096;
    public static final int MAX_ENCHANTMENTS = 1024;

    private NbtPayloadSanitizer() {}

    public static Result sanitize(CompoundTag source, int maximumBytes) {
        if (source == null) return new Result(new CompoundTag(), false, "null");
        Budget budget = new Budget(Math.max(1, maximumBytes));
        try {
            return new Result(copyCompound(source, 0, budget), false, "ok");
        } catch (LimitExceeded exception) {
            return new Result(new CompoundTag(), true, exception.reason);
        }
    }

    private static CompoundTag copyCompound(CompoundTag source, int depth, Budget budget) {
        checkDepth(depth);
        if (source.size() > MAX_CONTAINER_ENTRIES) throw new LimitExceeded("compound_entries");
        if (isCorruptEmptyTaczStack(source)) return canonicalEmptyStack();
        boolean marker = "spore:marker".equals(source.getString("forge:id"));
        CompoundTag copy = new CompoundTag();
        for (String key : source.getAllKeys()) {
            if (marker && "CurativeItems".equals(key)) continue;
            budget.consume(8L + key.getBytes(StandardCharsets.UTF_8).length);
            Tag child = source.get(key);
            if (child != null) copy.put(key, copyTag(child, depth + 1, budget));
        }
        return copy;
    }

    private static Tag copyTag(Tag source, int depth, Budget budget) {
        checkDepth(depth);
        if (source instanceof CompoundTag compound) return copyCompound(compound, depth, budget);
        if (source instanceof ListTag list) {
            if (list.size() > MAX_CONTAINER_ENTRIES) throw new LimitExceeded("list_entries");
            ListTag copy = new ListTag();
            for (Tag child : list) copy.add(copyTag(child, depth + 1, budget));
            return copy;
        }
        if (source instanceof ByteArrayTag bytes) budget.consume(bytes.getAsByteArray().length);
        else if (source instanceof IntArrayTag ints) budget.consume((long) ints.getAsIntArray().length * Integer.BYTES);
        else if (source instanceof LongArrayTag longs) budget.consume((long) longs.getAsLongArray().length * Long.BYTES);
        else if (source instanceof StringTag string) budget.consume(string.getAsString().getBytes(StandardCharsets.UTF_8).length);
        else if (source instanceof NumericTag) budget.consume(Long.BYTES);
        else budget.consume(16L);
        return source.copy();
    }

    private static boolean isCorruptEmptyTaczStack(CompoundTag stack) {
        String id = stack.contains("id", Tag.TAG_STRING) ? stack.getString("id") : "minecraft:air";
        int count = stack.contains("Count", Tag.TAG_ANY_NUMERIC) ? stack.getByte("Count") : 0;
        if (!"minecraft:air".equals(id) && count > 0 || !stack.contains("tag", Tag.TAG_COMPOUND)) return false;
        CompoundTag itemTag = stack.getCompound("tag");
        boolean tacz = itemTag.contains("GunDisplayId", Tag.TAG_STRING)
                || itemTag.contains("GunCurrentAmmoCount", Tag.TAG_ANY_NUMERIC)
                || itemTag.contains("GunId", Tag.TAG_STRING);
        return tacz && itemTag.contains("Enchantments", Tag.TAG_LIST)
                && itemTag.getList("Enchantments", Tag.TAG_COMPOUND).size() > MAX_ENCHANTMENTS;
    }

    private static CompoundTag canonicalEmptyStack() {
        CompoundTag stack = new CompoundTag();
        stack.putString("id", "minecraft:air");
        stack.putByte("Count", (byte) 0);
        return stack;
    }

    private static void checkDepth(int depth) {
        if (depth > MAX_DEPTH) throw new LimitExceeded("depth");
    }

    public record Result(CompoundTag tag, boolean truncated, String reason) {}

    private static final class Budget {
        private final long maximum;
        private long used;
        private Budget(long maximum) { this.maximum = maximum; }
        private void consume(long amount) {
            used += Math.max(0L, amount);
            if (used > maximum) throw new LimitExceeded("bytes");
        }
    }

    private static final class LimitExceeded extends RuntimeException {
        private final String reason;
        private LimitExceeded(String reason) { this.reason = reason; }
    }
}
