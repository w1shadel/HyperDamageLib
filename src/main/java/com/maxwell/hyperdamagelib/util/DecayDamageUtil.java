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

    // ★ ダメージ適用のコアメソッド
    public static void applyCustomDamage(LivingEntity target, DamageSource source, float rawAmount) {
        if (target.level().isClientSide()) return;

        // ダミー人形の場合は測定記録
        if (target instanceof MeasurementDummyEntity dummy) {
            dummy.recordDamageAbsolute(source, rawAmount);
            return;
        }

        // 自Modの無敵モードなら完全に無効化
        if (InvincibleHelper.isInvincible(target)) return;

        // 【異常値チェック】攻撃力・ターゲットHPが非数または無限大の場合 -> 即座に強制抹消
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

        // ==========================================
        // 1. Penetrate の計算（バニラ要素を考慮＋異常装甲の即死化）
        // ==========================================
        if (isPenetrate) {
            // 無敵時間 (i-frame) のチェック
            int invulnerableTime = entAcc.getInvulnerableTime();
            float lastHurt = livAcc.getLastHurt();
            if (invulnerableTime > 10) {
                if (rawAmount > lastHurt) {
                    finalDamage = rawAmount - lastHurt;
                    livAcc.setLastHurt(rawAmount);
                } else {
                    return; // i-frame中の低いダメージは通さない
                }
            } else {
                livAcc.setLastHurt(rawAmount);
                entAcc.setInvulnerableTime(20);
                target.hurtTime = 10;
            }

            // 防御力・タフネスの取得
            float armor = (float) target.getArmorValue();
            float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);

            // 【チート防御判定】NoSugar等の19億アーマーや非数を検知したら強制即死
            if (Float.isNaN(armor) || Float.isInfinite(armor) || armor > 1000.0F ||
                    Float.isNaN(toughness) || Float.isInfinite(toughness) || toughness > 1000.0F) {
                DecayForceKillHelper.decayForceKill(target);
                return;
            }

            // バニラ防御力計算
            finalDamage = CombatRules.getDamageAfterAbsorb(finalDamage, armor, toughness);

            // 耐性ポーションの計算
            if (target.hasEffect(MobEffects.DAMAGE_RESISTANCE)) {
                int amp = target.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier();
                if (amp >= 4) {
                    // 耐性5以上（100%カット）の不死化Modは貫通フェイルセーフで即死
                    DecayForceKillHelper.decayForceKill(target);
                    return;
                }
                finalDamage *= Math.max(0.1F, 1.0F - (amp + 1) * 0.20F);
            }
        }
        // ==========================================
        // 2. Erosion の計算（防御無視・最大HP削り）
        // ==========================================
        else if (isErosion) {
            finalDamage = rawAmount;
            // 侵食ゲージ（最大HPの削り）を蓄積
            if (target instanceof IDecayEntity decayTarget) {
                decayTarget.addDecayAmount(finalDamage);
            }
        }

        if (finalDamage <= 0.0F || Float.isNaN(finalDamage)) return;

        // ==========================================
        // 3. 実HPへの強制適用（NoSugar等の0化を突破）
        // ==========================================
        try {
            BYPASS_DECAY.set(true);
            float currentHealth = target.getEntityData().get(LivingEntityAccessor.getDataHealthId());
            if (Float.isNaN(currentHealth)) currentHealth = targetMaxHp;

            float nextHealth = Math.max(0.0F, currentHealth - finalDamage);

            target.setHealth(nextHealth);
            target.getEntityData().set(LivingEntityAccessor.getDataHealthId(), nextHealth);
            target.level().broadcastDamageEvent(target, source);
            target.markHurt();

            // 死亡判定
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