package com.maxwell.hyperdamagelib.util;

import com.maxwell.hyperdamagelib.entity.MeasurementDummyEntity;
import com.maxwell.hyperdamagelib.init.ModDamageTypes;
import com.maxwell.hyperdamagelib.mixin.accessor.EntityAccessor;
import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.maxwell.hyperdamagelib.network.ModMessages;
import com.maxwell.hyperdamagelib.network.client.ClientboundDecaySyncPacket;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public final class DecayDamageUtil {
    public static final ThreadLocal<Boolean> FORCE_DAMAGE = ThreadLocal.withInitial(() -> false);
    public static final ThreadLocal<Boolean> BYPASS_DECAY = ThreadLocal.withInitial(() -> false);
    public static final ThreadLocal<Boolean> BYPASS_EFFECT = ThreadLocal.withInitial(() -> false);

    private DecayDamageUtil() {
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
        if (target.level().isClientSide() || rawAmount <= 0.0F) return;
        if (target instanceof MeasurementDummyEntity dummy) {
            dummy.recordDamageAbsolute(source, rawAmount);
            return;
        }
        if (InvincibleHelper.isInvincible(target)) return;
        LivingEntityAccessor livAcc = (LivingEntityAccessor) target;
        EntityAccessor entAcc = (EntityAccessor) target;
        boolean isErosion = source.is(ModDamageTypes.EROSION);
        boolean isPenetrate = source.is(ModDamageTypes.PENETRATE);
        float finalDamage = rawAmount;
        float targetMaxHp = (float) target.getAttributeValue(Attributes.MAX_HEALTH);
        if (Float.isNaN(targetMaxHp) || targetMaxHp <= 0.0F) targetMaxHp = 20.0F;
        if (isPenetrate) {
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
        } else if (isErosion) {
            finalDamage = rawAmount;
            if (target instanceof IDecayEntity decayTarget) {
                decayTarget.addDecayAmount(finalDamage);
            }
        }
        if (finalDamage <= 0.0F) return;
        try {
            BYPASS_DECAY.set(true);
            float currentHealth = target.getEntityData().get(LivingEntityAccessor.getDataHealthId());
            if (Float.isNaN(currentHealth)) currentHealth = targetMaxHp;
            float decayAmount = (target instanceof IDecayEntity decay) ? decay.getDecayAmount() : 0.0F;
            float cappedMaxHealth = Math.max(0.0F, targetMaxHp - decayAmount);
            float nextHealth = Math.min(cappedMaxHealth, Math.max(0.0F, currentHealth - finalDamage));
            target.setHealth(nextHealth);
            sendDirectDataPacket(target, nextHealth);
            if (target instanceof IDecayEntity decayTarget) {
                ModMessages.INSTANCE.send(
                        PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                        new ClientboundDecaySyncPacket(
                                target.getId(),
                                decayTarget.getDecayAmount(),
                                decayTarget.isSuperInvincible(),
                                decayTarget.isKeepCurrentHealth(),
                                decayTarget.getInvincibleHealthValue(),
                                decayTarget.isHealBlocked()
                        )
                );
            }
            target.level().broadcastDamageEvent(target, source);
            target.markHurt();
            if (nextHealth <= 0.0F || (decayAmount >= targetMaxHp && targetMaxHp > 0.0F)) {
                boolean hasTotem = false;
                try {
                    hasTotem = livAcc.invokeCheckTotemDeathProtection(source);
                } catch (Throwable ignored) {
                }
                if (!hasTotem) {
                    if (target instanceof ServerPlayer sp && sp.connection != null) {
                        sp.connection.send(new ClientboundPlayerCombatKillPacket(sp.getId(), sp.getCombatTracker().getDeathMessage()));
                        target.die(source);
                    } else {
                        target.die(source);
                        if (target instanceof IDecayEntity decayTarget) {
                            decayTarget.setRemoveBypass(true);
                        }
                        target.remove(Entity.RemovalReason.KILLED);
                    }
                }
            } else {
                try {
                    livAcc.invokePlayHurtSound(source);
                } catch (Throwable ignored) {
                }
            }
            livAcc.setLastDamageSource(source);
            livAcc.setLastDamageStamp(target.level().getGameTime());

        } finally {
            BYPASS_DECAY.remove();
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
            if (target instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
                serverPlayer.connection.send(packet);
            }
            if (target.level() instanceof ServerLevel serverLevel) {
                serverLevel.getChunkSource().chunkMap.broadcast(target, packet);
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean forceAddEffect(LivingEntity target, MobEffectInstance instance, @Nullable Entity source) {
        if (target.level().isClientSide() || instance == null) return false;
        if (InvincibleHelper.isInvincible(target)) return false;
        try {
            BYPASS_EFFECT.set(true);
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
        } finally {
            BYPASS_EFFECT.remove();
        }
    }
}