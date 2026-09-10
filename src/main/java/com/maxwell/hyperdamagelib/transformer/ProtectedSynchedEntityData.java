package com.maxwell.hyperdamagelib.transformer;

import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.maxwell.hyperdamagelib.mixin.accessor.SynchedEntityDataAccessor;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.stream.Collectors;

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
            Int2ObjectMap<DataItem<?>> origMap = origAcc.getItemsById();

            for (Int2ObjectMap.Entry<DataItem<?>> entry : origMap.int2ObjectEntrySet()) {
                DataItem<?> origItem = entry.getValue();
                myAcc.getItemsById().put(entry.getIntKey(), new ProtectedDataItem<>(origItem, entity));
            }
        } catch (Throwable ignored) {}
    }
    public void hdl$forceSetHealth(float health) {
        try {
            EntityDataAccessor<Float> healthId = LivingEntityAccessor.getDataHealthId();
            SynchedEntityDataAccessor acc = (SynchedEntityDataAccessor) this;

            DataItem<Float> item = acc.invokeGetItem(healthId);
            if (item != null) {
                item.setValue(health);
                item.setDirty(true);
                acc.setIsDirty(true);
                this.ownerEntity.onSyncedDataUpdated(healthId);
            } else {
                super.set(healthId, health, true);
            }
        } catch (Throwable t) {
            try {
                super.set(LivingEntityAccessor.getDataHealthId(), health, true);
            } catch (Throwable ignored) {}
        }
    }
    public SynchedEntityData getOriginal() {
        return this.original;
    }

    @Override
    public <T> void set(EntityDataAccessor<T> key, T value, boolean force) {

        if (isOwnerInvincible() && isHealthDrop(key, value)) {
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
            return (T) Float.valueOf(DecayEntityMethods.hdl$getImmortalHealth(living));
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
    public List<DataValue<?>> packDirty() {
        return this.original.packDirty();
    }

    @Override
    public List<DataValue<?>> getNonDefaultValues() {
        return this.original.getNonDefaultValues();
    }

    @Override
    public void assignValues(List<DataValue<?>> entries) {
        if (isOwnerInvincible()) {
            try {
                int healthId = LivingEntityAccessor.getDataHealthId().getId();
                entries = entries.stream()
                        .filter(entry -> entry.id() != healthId)
                        .collect(Collectors.toList());
            } catch (Throwable ignored) {}
        }
        this.original.assignValues(entries);
    }

    @Override
    public boolean isEmpty() {
        return this.original.isEmpty();
    }

    public static boolean isOwnerInvincible(Entity entity) {
        return entity instanceof LivingEntity living && DecayEntityMethods.isP(living);
    }

    private boolean isOwnerInvincible() {
        return isOwnerInvincible(this.ownerEntity);
    }

    public static boolean isHealthKey(EntityDataAccessor<?> key) {
        try {
            return key.equals(LivingEntityAccessor.getDataHealthId());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isHealthDrop(EntityDataAccessor<?> key, Object value) {
        return false; 
    }

    public static class ProtectedDataItem<T> extends DataItem<T> {
        private final DataItem<T> delegate;
        private final Entity ownerEntity;

        @SuppressWarnings("unchecked")
        public ProtectedDataItem(DataItem<?> original, Entity entity) {
            super((EntityDataAccessor<T>) original.getAccessor(), (T) original.getValue());
            this.delegate = (DataItem<T>) original;
            this.ownerEntity = entity;
        }

        @Override
        public void setValue(T pValue) {
            if (ProtectedSynchedEntityData.isHealthKey(this.getAccessor()) &&
                    ProtectedSynchedEntityData.isOwnerInvincible(this.ownerEntity)) {
                if (pValue instanceof Float newHealth) {
                    LivingEntity living = (LivingEntity) this.ownerEntity;
                    float currentHealth = living.getHealth();
                    if (newHealth < currentHealth) {
                        return; 
                    }
                }
            }
            this.delegate.setValue(pValue);
            super.setValue(pValue);
        }

        @Override
        public T getValue() {
            if (ProtectedSynchedEntityData.isHealthKey(this.getAccessor()) &&
                    ProtectedSynchedEntityData.isOwnerInvincible(this.ownerEntity)) {
                if (this.ownerEntity instanceof LivingEntity living) {
                    return (T) Float.valueOf(DecayEntityMethods.hdl$getImmortalHealth(living));
                }
            }
            return this.delegate.getValue();
        }
    }

}