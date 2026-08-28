package com.arxyt.sporeperformance.diagnostics;

import net.minecraft.world.entity.Entity;

/** Stack value kept outside the Mixin package to satisfy runtime package-safety rules. */
public record EntityTickFrame(boolean measure, long started, Entity entity) {}
