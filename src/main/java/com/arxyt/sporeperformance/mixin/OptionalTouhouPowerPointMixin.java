package com.arxyt.sporeperformance.mixin;

import com.arxyt.sporeperformance.compat.PowerPointTickRuntime;
import com.arxyt.sporeperformance.diagnostics.PerformanceMetrics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Optional, signature-gated optimization for Touhou Little Maid's custom P-point entities. */
@Mixin(targets = "com.github.tartaricacid.touhoulittlemaid.entity.item.EntityPowerPoint", remap = false)
abstract class OptionalTouhouPowerPointMixin {
    @Shadow public int tickCount;
    @Unique private boolean sporeperformance$skipPhysics;

    @org.spongepowered.asm.mixin.injection.Inject(method = "m_8119_", at = @At("HEAD"))
    private void sporeperformance$decidePhysics(org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        sporeperformance$skipPhysics = PowerPointTickRuntime.shouldSkipPhysics((net.minecraft.world.entity.Entity) (Object) this, tickCount);
    }

    @Redirect(method = "followingMovement", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;m_45930_(Lnet/minecraft/world/entity/Entity;D)Lnet/minecraft/world/entity/player/Player;", remap = false), require = 0)
    private Player sporeperformance$indexedNearestPlayer(Level level, net.minecraft.world.entity.Entity source, double radius) {
        if (!PowerPointTickRuntime.enabled()) return level.getNearestPlayer(source, radius);
        return PowerPointTickRuntime.nearestPlayer(source, radius);
    }

    @Redirect(method = "m_8119_", at = @At(value = "INVOKE",
            target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/item/EntityPowerPoint;fluidMovement()V", remap = false), require = 0)
    private void sporeperformance$skipFluidMovement(Object self) {
        if (!sporeperformance$skipPhysics) fluidMovement();
    }

    @Redirect(method = "m_8119_", at = @At(value = "INVOKE",
            target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/item/EntityPowerPoint;groundMovement()V", remap = false), require = 0)
    private void sporeperformance$skipGroundMovement(Object self) {
        if (!sporeperformance$skipPhysics) groundMovement();
    }

    @Redirect(method = "m_8119_", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;m_6478_(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V", remap = false), require = 0)
    private void sporeperformance$skipMotion(Entity self, net.minecraft.world.entity.MoverType type, Vec3 delta) {
        if (!sporeperformance$skipPhysics) self.move(type, delta);
    }

    @Shadow abstract void fluidMovement();
    @Shadow abstract void groundMovement();
}
