package com.maxwell.hyperdamagelib.transformer;

import com.maxwell.hyperdamagelib.entity.MeasurementDummyEntity;
import com.maxwell.hyperdamagelib.init.ModDamageTypes;
import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.maxwell.hyperdamagelib.util.DecayDamageUtil;
import com.maxwell.hyperdamagelib.util.IDecayEntity;
import com.maxwell.hyperdamagelib.util.InvincibleHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Consumer;

public class DecayEntityMethods {
    public static boolean shouldInterceptSetPos(Entity entity, double x, double y, double z) {
        if (entity == null) return false;
        if (InvincibleHelper.isInvincible(entity) || entity instanceof MeasurementDummyEntity) {
            if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) ||
                    Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z) ||
                    Math.abs(x) > 29999984.0D || Math.abs(z) > 29999984.0D || Math.abs(y) > 20000000.0D) {
                return true;
            }
        }
        return false;
    }

    public static boolean shouldInterceptRemoval(Entity entity, Entity.RemovalReason reason) {
        if (entity == null || reason == null) return false;
        if (InvincibleHelper.isInvincible(entity)) {
            return !InvincibleHelper.isRemoveBypass(entity);
        }
        if (entity instanceof MeasurementDummyEntity dummy) {
            return !dummy.isRemoveBypass();
        }
        return false;
    }

    public static boolean shouldInterceptKill(Entity entity) {
        if (entity == null) return false;
        if (InvincibleHelper.isInvincible(entity)) {
            return true;
        }
        if (entity instanceof MeasurementDummyEntity dummy) {
            return !dummy.isRemoveBypass();
        }
        return false;
    }

    public static boolean shouldInterceptTickListRemove(Entity entity) {
        if (entity == null) return false;
        if (InvincibleHelper.isInvincible(entity)) {
            return !InvincibleHelper.isRemoveBypass(entity);
        }
        if (entity instanceof MeasurementDummyEntity dummy) {
            return !dummy.isRemoveBypass();
        }
        return false;
    }

    public static boolean shouldInterceptLookupRemove(Object entityAccessObj) {
        if (entityAccessObj instanceof Entity entity) {
            if (InvincibleHelper.isInvincible(entity)) {
                return !InvincibleHelper.isRemoveBypass(entity);
            }
            if (entity instanceof MeasurementDummyEntity dummy) {
                return !dummy.isRemoveBypass();
            }
        }
        return false;
    }

    public static boolean shouldInterceptCanBeAffected(Entity entity) {
        if (DecayDamageUtil.BYPASS_EFFECT.get()) return true;
        if (InvincibleHelper.isInvincible(entity)) return true;
        return false;
    }

    public static boolean replaceCanBeAffected(LivingEntity livingEntity) {
        if (DecayDamageUtil.BYPASS_EFFECT.get()) return true;
        if (InvincibleHelper.isInvincible(livingEntity)) return false;
        return true;
    }

    public static boolean shouldInterceptRemoveAllEffects(Entity entity) {
        return DecayDamageUtil.BYPASS_EFFECT.get();
    }

    public static boolean replaceRemoveAllEffects(LivingEntity livingEntity) {
        if (livingEntity.level().isClientSide()) return false;
        livingEntity.getActiveEffectsMap().clear();
        return true;
    }

    public static float getTrueHealth(LivingEntity livingEntity) {
        if (livingEntity == null) return 0.0F;
        if (livingEntity instanceof MeasurementDummyEntity dummy) {
            return dummy.isRemoveBypass() ? 0.0F : dummy.getMaxHealth();
        }
        if (InvincibleHelper.isInvincible(livingEntity)) {
            if (livingEntity instanceof IDecayEntity decay && decay.isKeepCurrentHealth()) {
                return decay.getInvincibleHealthValue();
            }
            float max = livingEntity.getMaxHealth();
            return (Float.isNaN(max) || max < 20.0F) ? 20.0F : max;
        }
        float maxHp = livingEntity.getMaxHealth();
        if (Float.isNaN(maxHp) || maxHp <= 0.0F) maxHp = 20.0F;
        float decayAmount = 0.0F;
        if (livingEntity instanceof IDecayEntity decay) {
            decayAmount = decay.getDecayAmount();
        }
        if (decayAmount >= maxHp) {
            return -Float.MAX_VALUE;
        }
        float rawHealth = getRawEntityDataHealth(livingEntity);
        float cappedMax = Math.max(0.0F, maxHp - decayAmount);
        return Math.max(0.0F, Math.min(rawHealth, cappedMax));
    }

    public static boolean isReallyAlive(Entity entity) {
        if (entity == null) return false;
        if (entity instanceof MeasurementDummyEntity dummy) {
            return !dummy.isRemoveBypass();
        }
        if (InvincibleHelper.isInvincible(entity)) {
            return true;
        }
        if (entity instanceof LivingEntity living) {
            return getTrueHealth(living) > 0.0F;
        }
        return entity.isAlive();
    }

    public static boolean isReallyDeadOrDying(LivingEntity livingEntity) {
        if (livingEntity == null) return true;
        if (livingEntity instanceof MeasurementDummyEntity dummy) {
            return dummy.isRemoveBypass();
        }
        if (InvincibleHelper.isInvincible(livingEntity)) {
            return false;
        }
        return getTrueHealth(livingEntity) <= 0.0F;
    }

    public static boolean interceptHurt(LivingEntity target, DamageSource source, float amount) {
        if (target == null || source == null) return false;
        if (InvincibleHelper.isInvincible(target)) {
            return true;
        }
        if (target instanceof MeasurementDummyEntity dummy) {
            if (dummy.level().isClientSide() || dummy.isRemoveBypass()) return false;
            dummy.recordDamageAbsolute(source, amount);
            return true;
        }
        if (source.is(ModDamageTypes.EROSION) || source.is(ModDamageTypes.PENETRATE)) {
            if (!target.level().isClientSide()) {
                DecayDamageUtil.applyCustomDamage(target, source, amount);
            }
            return true;
        }
        return false;
    }

    public static boolean shouldInterceptHurt(LivingEntity target, DamageSource source, float amount) {
        return interceptHurt(target, source, amount);
    }

    public static boolean handleForceDamage(LivingEntity target, DamageSource source, float amount) {
        return interceptHurt(target, source, amount);
    }

    public static boolean shouldReplaceHealthMethod(Entity entity) {
        if (DecayDamageUtil.BYPASS_DECAY.get()) return false;
        if (entity instanceof MeasurementDummyEntity) return true;
        if (InvincibleHelper.isInvincible(entity)) return true;
        if (entity instanceof LivingEntity living) {
            float rawHealth = getRawEntityDataHealth(living);
            float maxHealth = living.getMaxHealth();
            if (entity instanceof IDecayEntity decay && decay.getDecayAmount() > 0.0F) return true;
            if (rawHealth < maxHealth) return true;
        }
        return false;
    }

    public static float replaceGetHealth(LivingEntity livingEntity) {
        return getTrueHealth(livingEntity);
    }

    public static float getHealth(float incomingHealth, LivingEntity livingEntity) {
        return getTrueHealth(livingEntity);
    }

    private static float getRawEntityDataHealth(LivingEntity livingEntity) {
        try {
            Float val = livingEntity.getEntityData().get(LivingEntityAccessor.getDataHealthId());
            return (val != null && !Float.isNaN(val)) ? val : livingEntity.getMaxHealth();
        } catch (Throwable t) {
            return livingEntity.getMaxHealth();
        }
    }

    public static boolean replaceIsDeadOrDying(Entity entity) {
        if (entity instanceof LivingEntity living) return isReallyDeadOrDying(living);
        return false;
    }

    public static boolean isDeadOrDying(boolean deadOrDying, LivingEntity livingEntity) {
        return isReallyDeadOrDying(livingEntity);
    }

    public static boolean replaceIsAlive(Entity entity) {
        return isReallyAlive(entity);
    }

    public static boolean isAlive(boolean alive, Entity entity) {
        return isReallyAlive(entity);
    }

    public static Entity.RemovalReason getRemovalReason(Entity.RemovalReason removalReason, Entity entity) {
        if (entity instanceof MeasurementDummyEntity dummy && !dummy.isRemoveBypass()) return null;
        if (InvincibleHelper.isInvincible(entity) && !InvincibleHelper.isRemoveBypass(entity)) return null;
        return removalReason;
    }

    public static boolean isRemoved(boolean removed, Entity entity) {
        if (entity instanceof MeasurementDummyEntity dummy && !dummy.isRemoveBypass()) return false;
        if (InvincibleHelper.isInvincible(entity) && !InvincibleHelper.isRemoveBypass(entity)) return false;
        return removed;
    }

    public static boolean shouldReplaceIsPickable(Entity entity) {
        return true;
    }

    public static boolean replaceIsPickable(Entity entity) {
        if (InvincibleHelper.isInvincible(entity)) {
            return false;
        }
        return true;
    }

    public static boolean shouldReplaceIsAttackable(Entity entity) {
        return true;
    }

    public static boolean replaceIsAttackable(Entity entity) {
        if (InvincibleHelper.isInvincible(entity)) {
            return false;
        }
        return true;
    }

    public static boolean shouldReplaceCanBeHitByProjectile(Entity entity) {
        return true;
    }

    public static boolean replaceCanBeHitByProjectile(Entity entity) {
        if (InvincibleHelper.isInvincible(entity)) {
            return false;
        }
        return true;
    }

    public static boolean shouldReplaceIsPushable(Entity entity) {
        return true;
    }

    public static boolean replaceIsPushable(Entity entity) {
        if (InvincibleHelper.isInvincible(entity)) {
            return false;
        }
        return true;
    }

    public static boolean shouldInterceptEntityEvent(Entity entity, byte id) {
        if (entity == null) return false;
        if (InvincibleHelper.isInvincible(entity) && id == 3) {
            return true;
        }
        if (entity instanceof MeasurementDummyEntity dummy && id == 3) {
            return !dummy.isRemoveBypass();
        }
        return false;
    }

    public static boolean shouldOverrideTick(Entity entity) {
        return InvincibleHelper.isInvincible(entity);
    }

    public static void tickOverride(Consumer<Entity> consumer, Entity entity) {
        consumer.accept(entity);
    }
}