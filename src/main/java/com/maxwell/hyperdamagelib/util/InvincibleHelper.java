package com.maxwell.hyperdamagelib.util;

import com.maxwell.hyperdamagelib.network.ModMessages;
import com.maxwell.hyperdamagelib.network.client.ClientboundDecaySyncPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.PacketDistributor;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InvincibleHelper {
    public static final Set<UUID> SERVER_INVINCIBLE = ConcurrentHashMap.newKeySet();
    public static final Set<UUID> CLIENT_INVINCIBLE = ConcurrentHashMap.newKeySet();
    public static final Set<UUID> SERVER_REMOVE_BYPASS = ConcurrentHashMap.newKeySet();
    public static final Set<UUID> CLIENT_REMOVE_BYPASS = ConcurrentHashMap.newKeySet();

    public static boolean isInvincible(Entity entity) {
        if (entity == null) return false;
        if (entity.level().isClientSide()) {
            return CLIENT_INVINCIBLE.contains(entity.getUUID());
        }
        return SERVER_INVINCIBLE.contains(entity.getUUID());
    }

    public static void setInvincible(Entity entity, boolean invincible) {
        if (entity == null) return;
        UUID uuid = entity.getUUID();
        if (entity.level().isClientSide()) {
            if (invincible) CLIENT_INVINCIBLE.add(uuid);
            else CLIENT_INVINCIBLE.remove(uuid);
            return;
        }
        if (invincible) {
            SERVER_INVINCIBLE.add(uuid);
        } else {
            SERVER_INVINCIBLE.remove(uuid);
        }
        if (entity instanceof LivingEntity living) {
            ModMessages.INSTANCE.send(
                    PacketDistributor.ALL.noArg(),
                    new ClientboundDecaySyncPacket(entity.getId(), 0.0F, invincible, false, living.getMaxHealth(), false)
            );
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