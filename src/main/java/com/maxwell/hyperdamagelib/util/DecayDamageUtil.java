package com.maxwell.hyperdamagelib.util;

import com.maxwell.hyperdamagelib.entity.MeasurementDummyEntity;
import com.maxwell.hyperdamagelib.init.ModDamageTypes;
import com.maxwell.hyperdamagelib.mixin.accessor.EntityAccessor;
import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

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

    public static void applyCustomDamage(LivingEntity target, DamageSource source, float rawAmount) {
        if (target.level().isClientSide()) return;

        if (target instanceof MeasurementDummyEntity dummy) {
            dummy.recordDamageAbsolute(source, rawAmount);
            return;
        }

        if (InvincibleHelper.isInvincible(target)) return;

        float targetMaxHp = (float) target.getAttributeValue(Attributes.MAX_HEALTH);
        if (Float.isNaN(rawAmount) || Float.isInfinite(rawAmount) ||
                Float.isNaN(targetMaxHp) || Float.isInfinite(targetMaxHp) || targetMaxHp > 1000000.0F) {
            DecayForceKillHelper.decayForceKill(target);
            return;
        }

        LivingEntityAccessor livAcc = (LivingEntityAccessor) target;
        EntityAccessor entAcc = (EntityAccessor) target;
        boolean isErosion = source.is(ModDamageTypes.EROSION);
        boolean isPenetrate = source.is(ModDamageTypes.PENETRATE);

        float finalDamage = rawAmount;



        if (isPenetrate) {

            int invulnerableTime = entAcc.getInvulnerableTime();
            float lastHurt = livAcc.getLastHurt();
            if (invulnerableTime > 10) {
                if (rawAmount > lastHurt) {
                    finalDamage = rawAmount - lastHurt;
                    livAcc.setLastHurt(rawAmount);
                } else {
                    return; 
                }
            } else {
                livAcc.setLastHurt(rawAmount);
                entAcc.setInvulnerableTime(20);
                target.hurtTime = 10;
            }

            float armor = (float) target.getArmorValue();
            float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);

            if (Float.isNaN(armor) || Float.isInfinite(armor) || armor > 1000.0F ||
                    Float.isNaN(toughness) || Float.isInfinite(toughness) || toughness > 1000.0F) {
                DecayForceKillHelper.decayForceKill(target);
                return;
            }

            finalDamage = CombatRules.getDamageAfterAbsorb(finalDamage, armor, toughness);

            if (target.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
                int amp = target.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier();
                if (amp >= 4) {

                    DecayForceKillHelper.decayForceKill(target);
                    return;
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

        if (finalDamage <= 0.0F || Float.isNaN(finalDamage)) return;



        try {
            BYPASS_DECAY.set(true);
            float currentHealth = target.getEntityData().get(LivingEntityAccessor.getDataHealthId());
            if (Float.isNaN(currentHealth)) currentHealth = targetMaxHp;

            float nextHealth = Math.max(0.0F, currentHealth - finalDamage);

            target.setHealth(nextHealth);
            target.getEntityData().set(LivingEntityAccessor.getDataHealthId(), nextHealth);
            target.level().broadcastDamageEvent(target, source);
            target.markHurt();

            if (nextHealth <= 0.0F || target.isDeadOrDying()) {
                boolean hasTotem = false;
                try {
                    hasTotem = livAcc.invokeCheckTotemDeathProtection(source);
                } catch (Throwable ignored) {}

                if (!hasTotem) {
                    target.die(source);
                }
            } else {
                try {
                    livAcc.invokePlayHurtSound(source);
                } catch (Throwable ignored) {}
            }

            livAcc.setLastDamageSource(source);
            livAcc.setLastDamageStamp(target.level().getGameTime());

        } finally {
            BYPASS_DECAY.remove();
        }
    }
}