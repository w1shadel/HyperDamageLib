package com.maxwell.hyperdamagelib.util;

import net.minecraft.world.entity.Entity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InvincibleHelper {
    public static final Set<UUID> SERVER_REMOVE_BYPASS = ConcurrentHashMap.newKeySet();
    public static final Set<UUID> CLIENT_REMOVE_BYPASS = ConcurrentHashMap.newKeySet();

    public static boolean isInvincible(Entity entity) {
        if (entity == null) return false;
        if (entity instanceof IDecayEntity decay) {
            return decay.isSuperInvincible();
        }
        return false;
    }

    public static void setInvincible(Entity entity, boolean invincible) {
        if (entity instanceof IDecayEntity decay) {
            decay.setSuperInvincible(invincible);
        }
    }

    public static boolean isRemoveBypass(Entity entity) {
        if (entity == null) return false;
        if (entity.level().isClientSide()) {
            return CLIENT_REMOVE_BYPASS.contains(entity.getUUID());
        }
        return SERVER_REMOVE_BYPASS.contains(entity.getUUID());
    }

    public static void setRemoveBypass(Entity entity, boolean bypass) {
        if (entity == null) return;
        UUID uuid = entity.getUUID();
        if (entity.level().isClientSide()) {
            if (bypass) CLIENT_REMOVE_BYPASS.add(uuid);
            else CLIENT_REMOVE_BYPASS.remove(uuid);
        } else {
            if (bypass) SERVER_REMOVE_BYPASS.add(uuid);
            else SERVER_REMOVE_BYPASS.remove(uuid);
        }
    }
}