package com.maxwell.hyperdamagelib.transformer;

import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.maxwell.hyperdamagelib.mixin.accessor.SynchedEntityDataAccessor;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class ProtectedSynchedEntityData extends SynchedEntityData {
    private final SynchedEntityData original;
    private final Entity ownerEntity;

    public ProtectedSynchedEntityData(SynchedEntityData original, Entity entity) {
        super(entity);
        this.original = original;
        this.ownerEntity = entity;

        try {
            SynchedEntityDataAccessor myAcc = (SynchedEntityDataAccessor) this;
            SynchedEntityDataAccessor origAcc = (SynchedEntityDataAccessor) original;
            myAcc.getItemsById().putAll(origAcc.getItemsById());
        } catch (Throwable ignored) {}
    }

    @Override
    public <T> void set(EntityDataAccessor<T> key, T value, boolean force) {

        if (isHealthDrop(key, value)) {
            return; 
        }
        this.original.set(key, value, force);
    }

    @Override
    public <T> void set(EntityDataAccessor<T> key, T value) {
        this.set(key, value, false);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(EntityDataAccessor<T> key) {

        if (isHealthKey(key) && isOwnerInvincible()) {
            LivingEntity living = (LivingEntity) this.ownerEntity;
            return (T) Float.valueOf(living.getMaxHealth());
        }
        return this.original.get(key);
    }

    @Override
    public <T> void define(EntityDataAccessor<T> key, T value) {
        this.original.define(key, value);
    }

    @Override
    public <T> boolean hasItem(EntityDataAccessor<T> key) {
        return this.original.hasItem(key);
    }

    @Override
    public boolean isDirty() {
        return this.original.isDirty();
    }

    @Override
    public java.util.List<DataValue<?>> packDirty() {
        return this.original.packDirty();
    }

    @Override
    public java.util.List<DataValue<?>> getNonDefaultValues() {
        return this.original.getNonDefaultValues();
    }

    @Override
    public void assignValues(java.util.List<DataValue<?>> entries) {
        if (isOwnerInvincible()) {
            try {
                int healthId = LivingEntityAccessor.getDataHealthId().getId();

                entries = entries.stream()
                        .filter(entry -> entry.id() != healthId)
                        .collect(java.util.stream.Collectors.toList());
            } catch (Throwable ignored) {}
        }
        this.original.assignValues(entries);
    }

    @Override
    public boolean isEmpty() {
        return this.original.isEmpty();
    }




    private boolean isOwnerInvincible() {
        return this.ownerEntity instanceof LivingEntity living && DecayEntityMethods.isP(living);
    }

    private boolean isHealthKey(EntityDataAccessor<?> key) {
        try {

            return key.equals(LivingEntityAccessor.getDataHealthId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private <T> boolean isHealthDrop(EntityDataAccessor<T> key, T value) {

        if (isOwnerInvincible() && isHealthKey(key)) {
            if (value instanceof Float newHealth) {
                LivingEntity living = (LivingEntity) this.ownerEntity;
                float currentHealth = living.getHealth();

                return newHealth < currentHealth;
            }
        }
        return false;
    }
}