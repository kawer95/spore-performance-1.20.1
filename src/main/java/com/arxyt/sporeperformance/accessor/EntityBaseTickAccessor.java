package com.arxyt.sporeperformance.accessor;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Runtime-safe invoker added to Entity so multipart logic can retain base lifecycle work. */
@Mixin(Entity.class)
public interface EntityBaseTickAccessor {
    @Invoker("baseTick")
    void sporeperformance$baseTick();
}
