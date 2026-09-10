package com.maxwell.hyperdamagelib.util;

import com.maxwell.hyperdamagelib.entity.MeasurementDummyEntity;
import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.maxwell.hyperdamagelib.network.ModMessages;
import com.maxwell.hyperdamagelib.network.client.ClientboundDecaySyncPacket;
import com.maxwell.hyperdamagelib.transformer.ProtectedSynchedEntityData;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InvincibleHelper {
    public static final Set<UUID> SERVER_REMOVE_BYPASS = ConcurrentHashMap.newKeySet();
    public static final Set<UUID> CLIENT_REMOVE_BYPASS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> SUPER_INVINCIBLE = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Float> LOCKED_HEALTH = new ConcurrentHashMap<>();
    private static final Set<UUID> HEAL_BLOCKED = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> FORCE_UNINVINCIBLE_COOLDOWN = ConcurrentHashMap.newKeySet();

    private InvincibleHelper() {
    }

    public static void clearAllSessionData() {
        SUPER_INVINCIBLE.clear();
        LOCKED_HEALTH.clear();
        HEAL_BLOCKED.clear();
        SERVER_REMOVE_BYPASS.clear();
        CLIENT_REMOVE_BYPASS.clear();
    }

    public static boolean isInvincible(@Nullable Entity entity) {
        if (entity == null) return false;
        try {
            UUID uuid = entity.getUUID();
            return uuid != null && SUPER_INVINCIBLE.contains(uuid);
        } catch (Throwable ignored) {
            return false;
        }
    }
    public static void setInvincible(@Nullable Entity entity, boolean invincible) {
        if (entity == null) return;
        try {
            UUID uuid = entity.getUUID();
            if (uuid == null) return;

            if (invincible) {

                if (FORCE_UNINVINCIBLE_COOLDOWN.contains(uuid)) {
                    return;
                }

                if (entity instanceof LivingEntity living) {
                    Float rawHp = null;
                    try {
                        rawHp = living.getEntityData().get(LivingEntityAccessor.getDataHealthId());
                    } catch (Throwable ignored) {}

                    float currentHp = (rawHp != null && !Float.isNaN(rawHp) && rawHp > 0.0F)
                            ? rawHp : living.getHealth();
                    currentHp = Math.min(currentHp, living.getMaxHealth());

                    LOCKED_HEALTH.put(uuid, currentHp);
                }

                SUPER_INVINCIBLE.add(uuid);

                if (!(entity.entityData instanceof ProtectedSynchedEntityData)) {
                    try {
                        entity.entityData = new ProtectedSynchedEntityData(entity.entityData, entity);
                    } catch (Throwable ignored) {}
                }

                if (entity instanceof LivingEntity living) {
                    living.setInvulnerable(true);
                    keepAlive(living);
                }
            } else {

                SUPER_INVINCIBLE.remove(uuid);
                LOCKED_HEALTH.remove(uuid);

                FORCE_UNINVINCIBLE_COOLDOWN.add(uuid);
                TaskScheduler.schedule(() -> {
                    FORCE_UNINVINCIBLE_COOLDOWN.remove(uuid);
                }, 10);

                if (entity instanceof LivingEntity living) {
                    living.setInvulnerable(false);
                }

                if (entity.entityData instanceof ProtectedSynchedEntityData protectedData) {
                    try {
                        entity.entityData = protectedData.getOriginal();
                    } catch (Throwable ignored) {}
                }
            }

            if (entity instanceof LivingEntity living) {
                syncToTracking(living);
            }
        } catch (Throwable ignored) {}
    }

    public static float getInvincibleHealth(@Nullable LivingEntity entity) {
        if (entity == null) return 20.0F;
        try {
            UUID uuid = entity.getUUID();
            if (uuid != null) {
                Float locked = LOCKED_HEALTH.get(uuid);
                if (locked != null && locked > 0.0F) return locked;
            }
        } catch (Throwable ignored) {
        }
        return entity.getHealth() > 0.0F ? entity.getHealth() : 20.0F;
    }

    public static void setInvincibleHealth(@Nullable LivingEntity entity, float health) {
        if (entity == null) return;
        try {
            UUID uuid = entity.getUUID();
            if (uuid != null && health > 0.0F) {
                LOCKED_HEALTH.put(uuid, health);
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean isHealBlocked(@Nullable Entity entity) {
        if (entity == null) return false;
        try {
            UUID uuid = entity.getUUID();
            return uuid != null && HEAL_BLOCKED.contains(uuid);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void setHealBlocked(@Nullable Entity entity, boolean blocked) {
        if (entity == null) return;
        try {
            UUID uuid = entity.getUUID();
            if (uuid != null) {
                if (blocked) HEAL_BLOCKED.add(uuid);
                else HEAL_BLOCKED.remove(uuid);
                if (entity instanceof LivingEntity living) {
                    syncToTracking(living);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean isRemoveBypass(@Nullable Entity entity) {
        if (entity == null) return false;
        if (entity instanceof MeasurementDummyEntity dummy) {
            return dummy.isRemoveBypass();
        }
        try {
            UUID uuid = entity.getUUID();
            if (uuid == null) return false;
            return entity.level().isClientSide()
                    ? CLIENT_REMOVE_BYPASS.contains(uuid)
                    : SERVER_REMOVE_BYPASS.contains(uuid);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void setRemoveBypass(@Nullable Entity entity, boolean bypass) {
        if (entity == null) return;
        if (entity instanceof MeasurementDummyEntity dummy) {
            dummy.setRemoveBypass(bypass);
        }
        try {
            UUID uuid = entity.getUUID();
            if (uuid == null) return;
            Set<UUID> bypassSet = entity.level().isClientSide() ? CLIENT_REMOVE_BYPASS : SERVER_REMOVE_BYPASS;
            if (bypass) bypassSet.add(uuid);
            else bypassSet.remove(uuid);
        } catch (Throwable ignored) {
        }
    }

    public static boolean isDummy(@Nullable Entity entity) {
        return entity instanceof MeasurementDummyEntity;
    }

    public static void keepAlive(@Nullable LivingEntity entity) {
        if (entity == null) return;
        entity.dead = false;
        entity.deathTime = 0;
        if (entity.getPose() == Pose.DYING) {
            entity.setPose(Pose.STANDING);
        }
    }

    public static void syncToTracking(LivingEntity entity) {
        if (entity.level() != null && !entity.level().isClientSide()) {
            boolean invincible = isInvincible(entity);
            float targetHp = getInvincibleHealth(entity);
            boolean healBlocked = isHealBlocked(entity);
            ModMessages.INSTANCE.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                    new ClientboundDecaySyncPacket(
                            entity.getId(),
                            invincible,
                            true,
                            targetHp,
                            healBlocked
                    )
            );
            if (entity instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
                serverPlayer.connection.send(new ClientboundSetHealthPacket(
                        invincible ? targetHp : serverPlayer.getHealth(),
                        serverPlayer.getFoodData().getFoodLevel(),
                        serverPlayer.getFoodData().getSaturationLevel()
                ));
            }
        }
    }

    public static boolean shouldForceAttackable(Object obj) {
        if (obj instanceof Entity entity) {
            if (isInvincible(entity)) {
                return false;
            }
            return DecayDamageUtil.isForceDamage(entity);
        }
        return false;
    }
}