package com.maxwell.hyperdamagelib.util;

import com.maxwell.hyperdamagelib.entity.MeasurementDummyEntity;
import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InvincibleHelper {
    public static final Set<UUID> SERVER_REMOVE_BYPASS = ConcurrentHashMap.newKeySet();
    public static final Set<UUID> CLIENT_REMOVE_BYPASS = ConcurrentHashMap.newKeySet();

    private InvincibleHelper() {}

    public static boolean isInvincible(@Nullable Entity entity) {
        return entity instanceof IDecayEntity decay && decay.isSuperInvincible();
    }

    public static void setInvincible(@Nullable Entity entity, boolean invincible) {
        if (entity instanceof IDecayEntity decay) {
            decay.setSuperInvincible(invincible);
        }
    }

    public static float getInvincibleHealth(@Nullable LivingEntity entity) {
        if (entity instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            return decay.getInvincibleHealthValue();
        }
        return entity != null ? entity.getHealth() : 0.0F;
    }

    public static boolean isRemoveBypass(@Nullable Entity entity) {
        if (entity == null) return false;
        if (entity instanceof MeasurementDummyEntity dummy) {
            return dummy.isRemoveBypass();
        }
        if (entity instanceof IDecayEntity decay && decay.isRemoveBypass()) {
            return true;
        }
        UUID uuid = entity.getUUID();
        return entity.level().isClientSide()
                ? CLIENT_REMOVE_BYPASS.contains(uuid)
                : SERVER_REMOVE_BYPASS.contains(uuid);
    }

    public static void setRemoveBypass(@Nullable Entity entity, boolean bypass) {
        if (entity == null) return;
        if (entity instanceof MeasurementDummyEntity dummy) {
            dummy.setRemoveBypass(bypass);
        }
        if (entity instanceof IDecayEntity decay) {
            decay.setRemoveBypass(bypass);
        }
        UUID uuid = entity.getUUID();
        Set<UUID> bypassSet = entity.level().isClientSide() ? CLIENT_REMOVE_BYPASS : SERVER_REMOVE_BYPASS;
        if (bypass) {
            bypassSet.add(uuid);
        } else {
            bypassSet.remove(uuid);
        }
    }

    public static boolean isDummy(@Nullable Entity entity) {
        return entity instanceof MeasurementDummyEntity;
    }

    public static boolean isHealBlocked(@Nullable Entity entity) {
        return entity instanceof IDecayEntity decay && decay.isHealBlocked();
    }

    public static void setHealBlocked(@Nullable Entity entity, boolean blocked) {
        if (entity instanceof IDecayEntity decay) {
            decay.setHealBlocked(blocked);
        }
    }

    public static void keepAlive(@Nullable LivingEntity entity) {
        if (entity == null) return;
        entity.dead = false;
        entity.deathTime = 0;
        if (entity instanceof LivingEntityAccessor accessor) {
            accessor.setDeadFlag(false);
        }
        if (entity.getPose() == Pose.DYING) {
            entity.setPose(Pose.STANDING);
        }
    }
}