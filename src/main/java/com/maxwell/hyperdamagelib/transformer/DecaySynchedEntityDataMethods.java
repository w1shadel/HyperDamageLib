package com.maxwell.hyperdamagelib.transformer;

import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.maxwell.hyperdamagelib.util.DecayDamageUtil;
import com.maxwell.hyperdamagelib.util.DecaySecurityHelper;
import com.maxwell.hyperdamagelib.util.IDecayEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Field;

public class DecaySynchedEntityDataMethods {
    private static volatile Field entityField = null;
    private static volatile boolean initialized = false;

    private static void initField(Class<?> clazz) {
        if (initialized) return;
        synchronized (DecaySynchedEntityDataMethods.class) {
            if (initialized) return;
            try {
                entityField = clazz.getDeclaredField("entity");
                DecaySecurityHelper.forceSetAccessible(entityField);
            } catch (Throwable t) {
                try {
                    entityField = clazz.getDeclaredField("f_135344_");
                    DecaySecurityHelper.forceSetAccessible(entityField);
                } catch (Throwable ignored) {
                }
            }
            initialized = true;
        }
    }

    public static boolean handleForceSet(SynchedEntityData data, EntityDataAccessor<?> accessor, Object value) {
        if (DecayDamageUtil.BYPASS_DECAY.get()) {
            DecayDamageUtil.forceSetHealthVanillaRawDirect(data, accessor, value);
            return true;
        }
        return false;
    }

    public static Object onSet(SynchedEntityData data, EntityDataAccessor<?> accessor, Object value) {
        if (!initialized) {
            initField(data.getClass());
        }
        if (entityField == null) return value;
        try {
            Entity entity = (Entity) entityField.get(data);
            if (entity == null || entity.level().isClientSide()) {
                return value;
            }
            if (entity instanceof com.maxwell.hyperdamagelib.entity.MeasurementDummyEntity dummy) {
                if (!dummy.isRemoveBypass()) {
                    if (accessor.equals(LivingEntityAccessor.getDataHealthId())) {
                        return Float.valueOf(dummy.getMaxHealth());
                    }
                }
            }
            if (entity instanceof IDecayEntity decayEntity && entity instanceof LivingEntity living) {
                if (decayEntity.isSuperInvincible()) {
                    if (com.maxwell.hyperdamagelib.util.DecayDamageUtil.FORCE_DAMAGE.get()) {
                        return value;
                    }
                    if (accessor.equals(LivingEntityAccessor.getDataHealthId())) {
                        if (value instanceof Float) {
                            return Float.valueOf(decayEntity.getInvincibleHealthValue());
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return value;
    }
}