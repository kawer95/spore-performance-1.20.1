package com.arxyt.sporeperformance.compat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class NbtPayloadSanitizerTest {
    @Test
    void stripsMarkerCurativesWithoutMutatingSource() {
        CompoundTag root = new CompoundTag();
        CompoundTag effect = new CompoundTag();
        effect.putString("forge:id", "spore:marker");
        ListTag curatives = new ListTag();
        curatives.add(corruptStack(5000));
        effect.put("CurativeItems", curatives);
        root.put("effect", effect);

        NbtPayloadSanitizer.Result result = NbtPayloadSanitizer.sanitize(root, 524288);

        assertFalse(result.truncated());
        assertFalse(result.tag().getCompound("effect").contains("CurativeItems"));
        assertEquals(1, root.getCompound("effect").getList("CurativeItems", CompoundTag.TAG_COMPOUND).size());
    }

    @Test
    void rejectsOversizedUnrelatedListsWithoutTraversingThem() {
        CompoundTag root = new CompoundTag();
        ListTag list = new ListTag();
        for (int index = 0; index < 5000; index++) list.add(new CompoundTag());
        root.put("oversized", list);
        NbtPayloadSanitizer.Result result = NbtPayloadSanitizer.sanitize(root, 524288);
        assertTrue(result.truncated());
        assertTrue(result.tag().isEmpty());
        assertEquals("list_entries", result.reason());
    }

    @Test
    void preservesNormalEnchantedItems() {
        CompoundTag root = new CompoundTag();
        CompoundTag sword = new CompoundTag();
        sword.putString("id", "minecraft:diamond_sword");
        sword.putByte("Count", (byte) 1);
        CompoundTag itemTag = new CompoundTag();
        ListTag enchantments = new ListTag();
        CompoundTag sharpness = new CompoundTag();
        sharpness.putString("id", "minecraft:sharpness");
        sharpness.putShort("lvl", (short) 5);
        enchantments.add(sharpness);
        itemTag.put("Enchantments", enchantments);
        sword.put("tag", itemTag);
        root.put("item", sword);
        NbtPayloadSanitizer.Result result = NbtPayloadSanitizer.sanitize(root, 524288);
        assertFalse(result.truncated());
        assertEquals(root, result.tag());
    }

    private static CompoundTag corruptStack(int count) {
        CompoundTag stack = new CompoundTag();
        stack.putString("id", "minecraft:air");
        stack.putByte("Count", (byte) 0);
        CompoundTag itemTag = new CompoundTag();
        itemTag.putString("GunDisplayId", "sfms:lvsa_c_erode_1_display");
        ListTag enchantments = new ListTag();
        for (int index = 0; index < count; index++) {
            CompoundTag enchantment = new CompoundTag();
            enchantment.putString("id", "minecraft:protection");
            enchantment.putShort("lvl", (short) 3);
            enchantments.add(enchantment);
        }
        itemTag.put("Enchantments", enchantments);
        stack.put("tag", itemTag);
        return stack;
    }
}
