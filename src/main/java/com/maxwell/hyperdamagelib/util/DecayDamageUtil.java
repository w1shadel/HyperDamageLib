package com.maxwell.hyperdamagelib.util;

import com.maxwell.hyperdamagelib.entity.MeasurementDummyEntity;
import com.maxwell.hyperdamagelib.init.ModDamageTypes;
import com.maxwell.hyperdamagelib.mixin.accessor.EntityAccessor;
import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("removal")
public final class DecayDamageUtil {
    public static final TagKey<DamageType> ULTRA_BYPASS_DAMAGE =
            TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("hyperdamagelib", "ultra_bypass_damage"));
    private static final Set<UUID> FORCE_KILL_TARGETS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> BYPASS_DAMAGE_TARGETS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> BYPASS_EFFECT_TARGETS = ConcurrentHashMap.newKeySet();

    private DecayDamageUtil() {
    }

    public static void markPermanentlyKilled(Entity entity) {
        if (entity == null) return;
        UUID uuid = entity.getUUID();
        if (uuid != null) {
            FORCE_KILL_TARGETS.add(uuid);
        }
    }

    public static AutoCloseable bypassScope(@Nullable Entity entity) {
        if (entity == null) return () -> {
        };
        UUID uuid = entity.getUUID();
        if (uuid == null) return () -> {
        };
        BYPASS_DAMAGE_TARGETS.add(uuid);
        return () -> BYPASS_DAMAGE_TARGETS.remove(uuid);
    }

    public static AutoCloseable bypassEffectScope(@Nullable Entity entity) {
        if (entity == null) return () -> {
        };
        UUID uuid = entity.getUUID();
        if (uuid == null) return () -> {
        };
        BYPASS_EFFECT_TARGETS.add(uuid);
        return () -> BYPASS_EFFECT_TARGETS.remove(uuid);
    }

    public static AutoCloseable forceKillScope(@Nullable Entity entity) {
        if (entity == null) return () -> {
        };
        UUID uuid = entity.getUUID();
        if (uuid == null) return () -> {
        };
        FORCE_KILL_TARGETS.add(uuid);
        return () -> FORCE_KILL_TARGETS.remove(uuid);
    }

    public static boolean isForceDamage(@Nullable Entity entity) {
        if (entity == null) return false;
        UUID uuid = entity.getUUID();
        return uuid != null && FORCE_KILL_TARGETS.contains(uuid);
    }

    public static boolean isBypassDecay(@Nullable Entity entity) {
        if (entity == null) return false;
        UUID uuid = entity.getUUID();
        return uuid != null && BYPASS_DAMAGE_TARGETS.contains(uuid);
    }

    public static boolean isBypassEffect(@Nullable Entity entity) {
        if (entity == null) return false;
        UUID uuid = entity.getUUID();
        return uuid != null && BYPASS_EFFECT_TARGETS.contains(uuid);
    }

    public static DamageSource getErosionSource(Level level, @Nullable Entity attacker, @Nullable String customDeathMessage) {
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ModDamageTypes.EROSION);
        return createDamageSource(holder, attacker, customDeathMessage);
    }

    public static DamageSource getErosionSource(Level level, @Nullable Entity attacker) {
        return getErosionSource(level, attacker, null);
    }

    public static DamageSource getPenetrateSource(Level level, @Nullable Entity attacker, @Nullable String customDeathMessage) {
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ModDamageTypes.PENETRATE);
        return createDamageSource(holder, attacker, customDeathMessage);
    }

    public static DamageSource getPenetrateSource(Level level, @Nullable Entity attacker) {
        return getPenetrateSource(level, attacker, null);
    }

    private static DamageSource createDamageSource(Holder<DamageType> holder, @Nullable Entity attacker, @Nullable String customMessage) {
        return new DamageSource(holder, attacker) {
            @Override
            public Component getLocalizedDeathMessage(LivingEntity victim) {
                if (customMessage != null && !customMessage.isEmpty()) {
                    String victimName = victim.getDisplayName().getString();
                    String attackerName = attacker != null ? attacker.getDisplayName().getString() : "";
                    return Component.literal(customMessage.replace("%victim%", victimName).replace("%attacker%", attackerName));
                }
                return super.getLocalizedDeathMessage(victim);
            }
        };
    }

    public static void applyCustomDamage(LivingEntity target, DamageSource source, float rawAmount) {
        applyCustomDamage(target, source, rawAmount, false); 
    }
    public static void applyCustomDamage(LivingEntity target, DamageSource source, float rawAmount, boolean forceKill) {
        if (target.level().isClientSide() || rawAmount <= 0.0F) return;
        if (target instanceof MeasurementDummyEntity dummy) {
            dummy.recordDamageAbsolute(source, rawAmount);
            return;
        }
        if (InvincibleHelper.isInvincible(target)) return;

        LivingEntityAccessor livAcc = (LivingEntityAccessor) target;
        EntityAccessor entAcc = (EntityAccessor) target;
        float finalDamage = rawAmount;
        float targetMaxHp = (float) target.getAttributeValue(Attributes.MAX_HEALTH);

        if (Float.isNaN(targetMaxHp) || Float.isInfinite(targetMaxHp) || targetMaxHp <= 0.0F) {
            targetMaxHp = 20.0F;
        }

        if (source.is(ModDamageTypes.PENETRATE)) {
            int invTime = entAcc.getInvulnerableTime();
            float lastHurt = livAcc.getLastHurt();
            if (invTime > 10) {
                if (rawAmount <= lastHurt) return;
                finalDamage = rawAmount - lastHurt;
                livAcc.setLastHurt(rawAmount);
            } else {
                livAcc.setLastHurt(rawAmount);
                entAcc.setInvulnerableTime(20);
                target.hurtTime = 10;
            }
            float armor = Math.min(30.0F, (float) target.getArmorValue());
            float toughness = Math.min(20.0F, (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
            finalDamage = CombatRules.getDamageAfterAbsorb(finalDamage, armor, toughness);
            if (target.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
                int amp = Math.min(3, target.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier());
                finalDamage *= Math.max(0.20F, 1.0F - (amp + 1) * 0.20F);
            }
        } else if (source.is(ModDamageTypes.EROSION)) {
            finalDamage = rawAmount;
        }

        if (finalDamage <= 0.0F) return;

        try (var ignored = bypassScope(target)) {
            Float rawDataHp = target.getEntityData().get(LivingEntityAccessor.getDataHealthId());
            float currentHealth = (rawDataHp != null && !Float.isNaN(rawDataHp) && !Float.isInfinite(rawDataHp))
                    ? rawDataHp : targetMaxHp;

            float nextHealth;
            if (finalDamage >= Float.MAX_VALUE / 2 || Float.isInfinite(finalDamage)) {
                nextHealth = 0.0F;
            } else {
                nextHealth = Math.max(0.0F, currentHealth - finalDamage);
            }

            target.getCombatTracker().recordDamage(source, finalDamage);
            target.setHealth(nextHealth);
            sendDirectDataPacket(target, nextHealth);
            target.level().broadcastDamageEvent(target, source);
            target.markHurt();



            if (nextHealth <= 0.0F) {
                boolean hasTotem = false;
                try {
                    hasTotem = livAcc.invokeCheckTotemDeathProtection(source);
                } catch (Throwable ignored2) {}

                if (!hasTotem) {
                    if (forceKill) {

                        DecayForceKillHelper.decayForceKill(target, source);
                    } else {


                        target.die(source);
                    }
                }
            } else {
                try {
                    livAcc.invokePlayHurtSound(source);
                } catch (Throwable ignored2) {}
            }

            livAcc.setLastDamageSource(source);
            livAcc.setLastDamageStamp(target.level().getGameTime());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    private static void sendDirectDataPacket(LivingEntity target, float nextHealth) {
        try {
            SynchedEntityData.DataValue<Float> healthValue = SynchedEntityData.DataValue.create(
                    LivingEntityAccessor.getDataHealthId(),
                    nextHealth
            );
            ClientboundSetEntityDataPacket packet = new ClientboundSetEntityDataPacket(
                    target.getId(),
                    List.of(healthValue)
            );
            if (target.level() instanceof ServerLevel serverLevel) {
                serverLevel.getChunkSource().chunkMap.broadcast(target, packet);
            }
            if (target instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
                serverPlayer.connection.send(packet);
                serverPlayer.connection.send(new ClientboundSetHealthPacket(
                        nextHealth,
                        serverPlayer.getFoodData().getFoodLevel(),
                        serverPlayer.getFoodData().getSaturationLevel()
                ));
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean forceAddEffect(LivingEntity target, MobEffectInstance instance, @Nullable Entity source) {
        if (target.level().isClientSide() || instance == null) return false;
        if (InvincibleHelper.isInvincible(target)) return false;
        try (var ignored = bypassEffectScope(target)) {
            LivingEntityAccessor livAcc = (LivingEntityAccessor) target;
            Map<MobEffect, MobEffectInstance> activeEffects = livAcc.getActiveEffects();
            MobEffect effect = instance.getEffect();
            MobEffectInstance existing = activeEffects.get(effect);
            boolean isNew = (existing == null);
            if (isNew) {
                activeEffects.put(effect, instance);
                livAcc.invokeOnEffectAdded(instance, source);
            } else {
                if (existing.update(instance)) {
                    livAcc.invokeOnEffectUpdated(existing, true, source);
                }
            }
            ClientboundUpdateMobEffectPacket packet = new ClientboundUpdateMobEffectPacket(target.getId(), instance);
            if (target instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
                serverPlayer.connection.send(packet);
            }
            if (target.level() instanceof ServerLevel serverLevel) {
                serverLevel.getChunkSource().chunkMap.broadcast(target, packet);
            }
            return true;
        } catch (Throwable t) {
            return target.addEffect(instance, source);
        }
    }
}