package com.arxyt.sporeperformance.mixin;

import com.Harbinger.Spore.Sentities.Calamities.Stahlmorder;
import com.arxyt.sporeperformance.ai.FungalAiRuntime;
import com.arxyt.sporeperformance.ai.StahlAiControl;
import com.arxyt.sporeperformance.config.PerformanceConfig;
import com.arxyt.sporeperformance.diagnostics.DebugTrace;
import com.arxyt.sporeperformance.world.StahlRisingBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = Stahlmorder.class, remap = false, priority = 900)
abstract class AiFixStahlmorderControlMixin implements StahlAiControl {
    @Unique private Vec3 sporeperformance$controlledLeapTarget;
    @Unique private int sporeperformance$controlledLeapTicks;
    @Unique private boolean sporeperformance$wasAirborne;

    @Invoker("decideAnimation")
    protected abstract int sporeperformance$invokeDecideAnimation(LivingEntity target);

    @Invoker("applyAttackEffect")
    protected abstract void sporeperformance$invokeApplyAttackEffect(LivingEntity target, int state);

    @Override
    public int sporeperformance$decideAnimation(LivingEntity target) {
        return sporeperformance$invokeDecideAnimation(target);
    }

    @Override
    public void sporeperformance$applyAttackEffect(LivingEntity target, int state) {
        sporeperformance$invokeApplyAttackEffect(target, state);
    }

    @Override
    public void sporeperformance$beginControlledLeap(Vec3 landingTarget) {
        sporeperformance$controlledLeapTarget = landingTarget;
        sporeperformance$controlledLeapTicks = 80;
        sporeperformance$wasAirborne = true;
    }

    @ModifyArg(method = "createAttributes", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier$Builder;add(Lnet/minecraft/world/entity/ai/attributes/Attribute;D)Lnet/minecraft/world/entity/ai/attributes/AttributeSupplier$Builder;",
            ordinal = 1, remap = true), index = 1, require = 0)
    private static double sporeperformance$fixedMovementSpeed(double original) {
        return PerformanceConfig.REFACTOR_AI_ENABLED.get() ? 0.34D : original;
    }

    @Inject(method = "m_8119_", at = @At("TAIL"), remap = false)
    private void sporeperformance$tickControlledLeap(CallbackInfo callback) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get() || sporeperformance$controlledLeapTarget == null) return;
        Stahlmorder stahl = (Stahlmorder) (Object) this;
        if (stahl.onGround() && sporeperformance$wasAirborne) {
            if (DebugTrace.enabled(DebugTrace.Category.STAHL) && stahl.level() instanceof ServerLevel serverLevel)
                DebugTrace.event(DebugTrace.Category.STAHL, serverLevel, DebugTrace.trace(stahl), stahl,
                        "leap_landed", "ticksRemaining=" + sporeperformance$controlledLeapTicks
                                + ",landingTarget=" + sporeperformance$controlledLeapTarget);
            sporeperformance$landingImpact(stahl);
            sporeperformance$clearControlledLeap();
            return;
        }
        if (--sporeperformance$controlledLeapTicks <= 0 || !stahl.isAlive()) {
            if (DebugTrace.enabled(DebugTrace.Category.STAHL) && stahl.level() instanceof ServerLevel serverLevel)
                DebugTrace.event(DebugTrace.Category.STAHL, serverLevel, DebugTrace.trace(stahl), stahl,
                        "leap_cancelled", "alive=" + stahl.isAlive());
            sporeperformance$clearControlledLeap();
            return;
        }

        Vec3 delta = stahl.getDeltaMovement();
        if (delta.y > 0.0D) {
            stahl.setDeltaMovement(delta.x * 0.94D, delta.y * 0.965D, delta.z * 0.94D);
            return;
        }
        Vec3 toLanding = new Vec3(sporeperformance$controlledLeapTarget.x - stahl.getX(), 0.0D,
                sporeperformance$controlledLeapTarget.z - stahl.getZ());
        Vec3 steering = Vec3.ZERO;
        if (toLanding.lengthSqr() > 1.0E-7D) {
            steering = toLanding.normalize().scale(Mth.clamp(toLanding.horizontalDistance() * 0.075D, 0.0D, 0.24D));
        }
        double fallSpeed = Math.max(delta.y - 0.075D, -1.65D);
        stahl.setDeltaMovement(delta.x * 0.985D + steering.x, fallSpeed, delta.z * 0.985D + steering.z);
    }

    @Inject(method = "decideAnimation", at = @At("HEAD"), cancellable = true)
    private void sporeperformance$preferFunctionalSwordArm(LivingEntity target, CallbackInfoReturnable<Integer> callback) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get()) return;
        Stahlmorder stahl = (Stahlmorder) (Object) this;
        if (stahl.getSwordArmHp() <= 0) return;
        float slashChance = target.getArmorValue() >= 10 ? 0.85F : 0.70F;
        if (stahl.getRandom().nextFloat() < slashChance) callback.setReturnValue(0);
        else callback.setReturnValue(stahl.getRandom().nextFloat() < 0.05F ? 2 : 1);
    }

    @Inject(method = "m_7350_", at = @At("TAIL"), remap = false)
    private void sporeperformance$fixedSlashDamage(EntityDataAccessor<?> key, CallbackInfo callback) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get() || !Stahlmorder.MELEE_STATE.equals(key)) return;
        Stahlmorder stahl = (Stahlmorder) (Object) this;
        if (stahl.getMeleeState().getValue() != 0) return;
        AttributeInstance damage = stahl.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(45.0D);
    }

    @Inject(method = "m_7327_", at = @At("TAIL"), remap = false)
    private void sporeperformance$extraHitEffects(Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get() || !callback.getReturnValue()
                || !(entity instanceof LivingEntity living)) return;
        Stahlmorder stahl = (Stahlmorder) (Object) this;
        int state = stahl.getMeleeState().getValue();
        if (state == 0) {
            living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1), stahl);
            stahl.heal(10.0F);
        } else if (state == 2) {
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 4), stahl);
        }
    }

    @Inject(method = "m_7822_", at = @At("TAIL"), remap = false)
    private void sporeperformance$landingClientEffects(byte eventId, CallbackInfo callback) {
        if (!PerformanceConfig.REFACTOR_AI_ENABLED.get() || eventId != 67) return;
        Stahlmorder stahl = (Stahlmorder) (Object) this;
        if (!stahl.level().isClientSide) return;
        Level level = stahl.level();
        Vec3 center = stahl.position();
        BlockParticleOption block = new BlockParticleOption(ParticleTypes.BLOCK,
                sporeperformance$findGroundState(level, stahl.blockPosition()));
        for (int i = 0; i < 120; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double radius = Math.sqrt(level.random.nextDouble()) * 2.9D;
            double speed = 0.28D + level.random.nextDouble() * 0.55D;
            level.addParticle(block, center.x + Math.cos(angle) * radius,
                    center.y + 0.05D + level.random.nextDouble() * 0.35D,
                    center.z + Math.sin(angle) * radius,
                    Math.cos(angle) * speed + (level.random.nextDouble() - 0.5D) * 0.16D,
                    0.22D + level.random.nextDouble() * 0.62D,
                    Math.sin(angle) * speed + (level.random.nextDouble() - 0.5D) * 0.16D);
        }
        for (int i = 0; i < 72; i++) {
            double angle = Math.PI * 2.0D * i / 72.0D;
            double radius = 2.5D + level.random.nextDouble() * 3.7D;
            double speed = 0.18D + level.random.nextDouble() * 0.32D;
            level.addParticle(block, center.x + Math.cos(angle) * radius, center.y + 0.12D,
                    center.z + Math.sin(angle) * radius, Math.cos(angle) * speed,
                    0.18D + level.random.nextDouble() * 0.3D, Math.sin(angle) * speed);
        }
        for (int i = 0; i < 24; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            double radius = 1.0D + level.random.nextDouble() * 4.6D;
            level.addParticle(ParticleTypes.CLOUD, center.x + Math.cos(angle) * radius, center.y + 0.08D,
                    center.z + Math.sin(angle) * radius, Math.cos(angle) * 0.12D, 0.03D, Math.sin(angle) * 0.12D);
        }
    }

    @Unique
    private void sporeperformance$landingImpact(Stahlmorder stahl) {
        stahl.playSound(SoundEvents.GENERIC_EXPLODE, 1.35F, 0.75F + stahl.getRandom().nextFloat() * 0.15F);
        stahl.playSound(SoundEvents.RAVAGER_STEP, 1.8F, 0.55F);
        stahl.level().broadcastEntityEvent(stahl, (byte) 67);
        AABB area = stahl.getBoundingBox().inflate(6.0D, 1.5D, 6.0D);
        List<LivingEntity> targets;
        if (PerformanceConfig.REFACTOR_SHARED_PERCEPTION.get() && stahl.level() instanceof ServerLevel serverLevel) {
            targets = FungalAiRuntime.query(serverLevel, stahl, area, LivingEntity.class);
        } else {
            targets = stahl.level().getEntitiesOfClass(LivingEntity.class, area);
        }
        for (LivingEntity target : targets) {
            if (target == stahl || !stahl.TARGET_SELECTOR.test(target) || !area.intersects(target.getBoundingBox())) continue;
            double distance = Math.max(1.0D, target.distanceTo(stahl));
            float damage = (float) (12.0D * Mth.clamp(1.25D - distance / 6.0D, 0.35D, 1.0D));
            target.hurt(stahl.damageSources().mobAttack(stahl), damage);
            Vec3 push = target.position().subtract(stahl.position()).multiply(1.0D, 0.0D, 1.0D);
            if (push.lengthSqr() <= 1.0E-7D) {
                push = new Vec3(stahl.getRandom().nextDouble() - 0.5D, 0.0D, stahl.getRandom().nextDouble() - 0.5D);
            }
            push = push.normalize().scale(1.15D * Mth.clamp(1.2D - distance / 6.0D, 0.35D, 1.0D));
            target.push(push.x, 0.45D, push.z);
            target.hurtMarked = true;
            if (DebugTrace.enabled(DebugTrace.Category.STAHL) && stahl.level() instanceof ServerLevel serverLevel)
                DebugTrace.event(DebugTrace.Category.STAHL, serverLevel, DebugTrace.trace(stahl), stahl,
                        "landing_hit", "target=" + target.getUUID() + ",damage=" + damage + ",distance=" + distance);
        }
        if (stahl.level() instanceof ServerLevel serverLevel) {
            BlockState ground = sporeperformance$findGroundState(serverLevel, stahl.blockPosition());
            Vec3 center = stahl.position();
            BlockParticleOption block = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            serverLevel.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.15D, center.z,
                    2, 0.45D, 0.05D, 0.45D, 0.02D);
            serverLevel.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.1D, center.z,
                    18, 1.8D, 0.05D, 1.8D, 0.08D);
            serverLevel.sendParticles(block, center.x, center.y + 0.25D, center.z,
                    90, 2.6D, 0.35D, 2.6D, 0.35D);
            for (int i = 0; i < 36; i++) {
                double angle = Math.PI * 2.0D * i / 36.0D;
                double radius = 2.0D + serverLevel.random.nextDouble() * 3.6D;
                serverLevel.sendParticles(block, center.x + Math.cos(angle) * radius, center.y + 0.12D,
                        center.z + Math.sin(angle) * radius, 3, 0.18D, 0.08D, 0.18D, 0.18D);
            }
            sporeperformance$spawnRisingBlocks(serverLevel, center);
            if (DebugTrace.enabled(DebugTrace.Category.STAHL))
                DebugTrace.event(DebugTrace.Category.STAHL, serverLevel, DebugTrace.trace(stahl), stahl,
                        "landing_effects_sent", "aoeCandidates=" + targets.size());
        }
    }

    @Unique
    private void sporeperformance$spawnRisingBlocks(ServerLevel level, Vec3 center) {
        int minX = Mth.floor(center.x - 5.6D), maxX = Mth.ceil(center.x + 5.6D);
        int minZ = Mth.floor(center.z - 5.6D), maxZ = Mth.ceil(center.z + 5.6D);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int spawned = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double dx = x + 0.5D - center.x, dz = z + 0.5D - center.z;
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > 5.6D || distance < 0.75D) continue;
                if (level.random.nextFloat() > Mth.clamp(1.15D - distance / 5.6D, 0.18D, 0.72D)) continue;
                int y = sporeperformance$findGroundY(level, x, z, Mth.floor(center.y));
                pos.set(x, y, z);
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.hasBlockEntity() || level.getBlockState(pos.above()).blocksMotion()) continue;
                double outward = Math.max(distance, 0.001D);
                double bounce = Mth.clamp(0.92D - distance / 5.6D * 0.38D + level.random.nextDouble() * 0.22D,
                        0.38D, 0.92D);
                Vec3 velocity = new Vec3(dx / outward * 0.12D, bounce, dz / outward * 0.12D);
                int life = 22 + level.random.nextInt(14) + Mth.floor(distance * 2.0D);
                level.addFreshEntity(new StahlRisingBlockEntity(level, x + 0.5D, y + 1.0D, z + 0.5D,
                        state, life, velocity));
                spawned++;
            }
        }
        if (DebugTrace.enabled(DebugTrace.Category.STAHL))
            DebugTrace.event(DebugTrace.Category.STAHL, level, 0L, null,
                    "rising_blocks_spawned", "count=" + spawned + ",center=" + center);
    }

    @Unique
    private int sporeperformance$findGroundY(Level level, int x, int z, int originY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, originY, z);
        if (!level.getBlockState(pos).isAir()) return level.getBlockState(pos.above()).isAir() ? originY : originY + 1;
        for (int offset = 1; offset <= 5; offset++) {
            pos.set(x, originY - offset, z);
            if (!level.getBlockState(pos).isAir()) return originY - offset;
        }
        return originY;
    }

    @Unique
    private BlockState sporeperformance$findGroundState(Level level, BlockPos origin) {
        BlockPos.MutableBlockPos pos = origin.mutable();
        for (int offset = 0; offset <= 4; offset++) {
            pos.set(origin.getX(), origin.getY() - offset, origin.getZ());
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) return state;
        }
        return level.getBlockState(origin.below());
    }

    @Unique
    private void sporeperformance$clearControlledLeap() {
        sporeperformance$controlledLeapTarget = null;
        sporeperformance$controlledLeapTicks = 0;
        sporeperformance$wasAirborne = false;
    }
}
