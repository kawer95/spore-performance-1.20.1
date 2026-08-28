package com.arxyt.sporeperformance.world;

import com.arxyt.sporeperformance.registry.PerformanceEntities;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public final class StahlRisingBlockEntity extends Entity {
    private static final EntityDataAccessor<BlockState> BLOCK_STATE =
            SynchedEntityData.defineId(StahlRisingBlockEntity.class, EntityDataSerializers.BLOCK_STATE);
    public float spinX;
    public float spinY;
    public float spinZ;
    private int life = 26;

    public StahlRisingBlockEntity(EntityType<? extends StahlRisingBlockEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public StahlRisingBlockEntity(Level level, double x, double y, double z, BlockState state, int life, Vec3 velocity) {
        this(PerformanceEntities.STAHL_RISING_BLOCK.get(), level);
        setPos(x, y, z);
        setBlockState(state);
        this.life = life;
        setDeltaMovement(velocity);
        spinX = (level.random.nextFloat() - 0.5F) * 24.0F;
        spinY = (level.random.nextFloat() - 0.5F) * 36.0F;
        spinZ = (level.random.nextFloat() - 0.5F) * 24.0F;
    }

    @Override protected void defineSynchedData() { entityData.define(BLOCK_STATE, Blocks.STONE.defaultBlockState()); }
    public BlockState getBlockState() { return entityData.get(BLOCK_STATE); }
    public void setBlockState(BlockState state) { entityData.set(BLOCK_STATE, state); }

    @Override public void tick() {
        super.tick();
        if (tickCount > life) { discard(); return; }
        Vec3 velocity = getDeltaMovement();
        setPos(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
        setDeltaMovement(velocity.x * 0.96D, (velocity.y - 0.075D) * 0.96D, velocity.z * 0.96D);
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        setBlockState(NbtUtils.readBlockState(level().holderLookup(Registries.BLOCK), tag.getCompound("BlockState")));
        life = tag.getInt("Life");
        spinX = tag.getFloat("SpinX"); spinY = tag.getFloat("SpinY"); spinZ = tag.getFloat("SpinZ");
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("BlockState", NbtUtils.writeBlockState(getBlockState()));
        tag.putInt("Life", life);
        tag.putFloat("SpinX", spinX); tag.putFloat("SpinY", spinY); tag.putFloat("SpinZ", spinZ);
    }

    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
