package com.maxwell.hyperdamagelib.util;

import com.maxwell.hyperdamagelib.entity.MeasurementDummyEntity;
import com.maxwell.hyperdamagelib.init.ModDamageTypes;
import com.maxwell.hyperdamagelib.mixin.accessor.EntityAccessor;
import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import javax.annotation.Nullable;

public final class DecayDamageUtil {
    public static final ThreadLocal<Boolean> FORCE_DAMAGE = ThreadLocal.withInitial(() -> false);
    public static final ThreadLocal<Boolean> BYPASS_DECAY = ThreadLocal.withInitial(() -> false);
    public static final ThreadLocal<Boolean> BYPASS_EFFECT = ThreadLocal.withInitial(() -> false);

    private DecayDamageUtil() {}

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
                    Component formatted = formatCustomMessage(customMessage, victim, this.getEntity());
                    if (formatted != null) return formatted;
                }
                return super.getLocalizedDeathMessage(victim);
            }
        };
    }

    public static Component formatCustomMessage(String template, LivingEntity victim, @Nullable Entity attacker) {
        if (template == null || template.isEmpty()) return null;
        String victimName = victim.getDisplayName().getString();
        String attackerName = attacker != null ? attacker.getDisplayName().getString() : "";
        return Component.literal(template.replace("%victim%", victimName).replace("%attacker%", attackerName));
    }

    public static boolean applyCustomDamage(LivingEntity target, DamageSource source, float rawAmount) {
        if (target.level().isClientSide() || rawAmount <= 0.0F) return false;

        if (target instanceof MeasurementDummyEntity dummy) {
            dummy.recordDamageAbsolute(source, rawAmount);
            return true;
        }

        if (InvincibleHelper.isInvincible(target)) return false;

        float targetMaxHp = (float) target.getAttributeValue(Attributes.MAX_HEALTH);
        if (Float.isNaN(rawAmount) || Float.isInfinite(rawAmount) ||
                Float.isNaN(targetMaxHp) || Float.isInfinite(targetMaxHp) || targetMaxHp > 1000000.0F) {
            DecayForceKillHelper.decayForceKill(target);
            return true;
        }

        LivingEntityAccessor livAcc = (LivingEntityAccessor) target;
        EntityAccessor entAcc = (EntityAccessor) target;
        boolean isErosion = source.is(ModDamageTypes.EROSION);
        boolean isPenetrate = source.is(ModDamageTypes.PENETRATE);

        float finalDamage = rawAmount;
        boolean isBlocked = false;



        if (isPenetrate) {

            if (target.isDamageSourceBlocked(source)) {
                isBlocked = true;
                target.hurtCurrentlyUsedShield(rawAmount);
                finalDamage = Math.max(0.0F, rawAmount * 0.25F); 
                target.level().broadcastEntityEvent(target, (byte) 29); 
            }

            int invulnerableTime = entAcc.getInvulnerableTime();
            float lastHurt = livAcc.getLastHurt();
            if (invulnerableTime > 10) {
                if (finalDamage <= lastHurt) {
                    return false; 
                }
                float diff = finalDamage - lastHurt;
                livAcc.setLastHurt(finalDamage);
                finalDamage = diff;
            } else {
                livAcc.setLastHurt(finalDamage);
                entAcc.setInvulnerableTime(20);
                target.hurtDuration = 10;
                target.hurtTime = 10;
            }

            float armor = (float) target.getArmorValue();
            float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
            if (Float.isNaN(armor) || Float.isInfinite(armor) || armor > 1000.0F ||
                    Float.isNaN(toughness) || Float.isInfinite(toughness) || toughness > 1000.0F) {
                DecayForceKillHelper.decayForceKill(target);
                return true;
            }

            finalDamage = CombatRules.getDamageAfterAbsorb(finalDamage, armor, toughness);

            if (target.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
                int amp = target.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier();
                if (amp >= 4) {

                    DecayForceKillHelper.decayForceKill(target);
                    return true;
                }
                finalDamage *= Math.max(0.1F, 1.0F - (amp + 1) * 0.20F);
            }
        }
        else if (isErosion) {

            finalDamage = rawAmount;
            if (target instanceof IDecayEntity decayTarget) {
                decayTarget.addDecayAmount(finalDamage); 
            }
        }

        if (finalDamage <= 0.0F || Float.isNaN(finalDamage)) return false;



        try {
            BYPASS_DECAY.set(true);

            float absorption = target.getAbsorptionAmount();
            if (absorption > 0.0F) {
                float absorbed = Math.min(absorption, finalDamage);
                target.setAbsorptionAmount(absorption - absorbed);
                finalDamage -= absorbed;
            }

            float currentHealth = target.getEntityData().get(LivingEntityAccessor.getDataHealthId());
            if (Float.isNaN(currentHealth)) currentHealth = targetMaxHp;
            float nextHealth = Math.max(0.0F, currentHealth - finalDamage);

            target.setHealth(nextHealth);
            target.getEntityData().set(LivingEntityAccessor.getDataHealthId(), nextHealth);

            target.getCombatTracker().recordDamage(source, finalDamage);
            target.gameEvent(GameEvent.ENTITY_DAMAGE);



            if (!isBlocked) {
                target.level().broadcastDamageEvent(target, source);
                target.markHurt();

                Entity attacker = source.getEntity();
                if (attacker != null) {
                    double dx = attacker.getX() - target.getX();
                    double dz = attacker.getZ() - target.getZ();
                    while (dx * dx + dz * dz < 1.0E-4) {
                        dx = (Math.random() - Math.random()) * 0.01;
                        dz = (Math.random() - Math.random()) * 0.01;
                    }
                    target.knockback(0.4F, dx, dz);
                    target.indicateDamage(dx, dz);
                }
            }



            Entity attacker = source.getEntity();
            if (attacker instanceof LivingEntity livingAttacker) {
                target.setLastHurtByMob(livingAttacker);
            }
            if (attacker instanceof Player playerAttacker) {
                target.lastHurtByPlayerTime = 100;
                livAcc.setLastHurtByPlayer(playerAttacker);
            } else if (attacker instanceof TamableAnimal tamable && tamable.isTame()) {
                if (tamable.getOwner() instanceof Player ownerPlayer) {
                    target.lastHurtByPlayerTime = 100;
                    livAcc.setLastHurtByPlayer(ownerPlayer);
                }
            }

            livAcc.setLastDamageSource(source);
            livAcc.setLastDamageStamp(target.level().getGameTime());



            if (nextHealth <= 0.0F || target.isDeadOrDying()) {
                boolean hasTotem = false;
                try {
                    hasTotem = livAcc.invokeCheckTotemDeathProtection(source);
                } catch (Throwable ignored) {}

                if (!hasTotem) {
                    SoundEvent deathSound = target.getDeathSound();
                    if (deathSound != null) {
                        target.playSound(deathSound, target.getSoundVolume(), target.getVoicePitch());
                    }
                    target.die(source);
                }
            } else if (!isBlocked) {
                try {
                    livAcc.invokePlayHurtSound(source);
                } catch (Throwable ignored) {}
            }

            return true;

        } finally {
            BYPASS_DECAY.remove();
        }
    }
}