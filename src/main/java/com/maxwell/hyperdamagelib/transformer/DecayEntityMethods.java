package com.maxwell.hyperdamagelib.transformer;

import com.maxwell.hyperdamagelib.entity.MeasurementDummyEntity;
import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.maxwell.hyperdamagelib.util.DecayDamageUtil;
import com.maxwell.hyperdamagelib.util.IDecayEntity;
import com.maxwell.hyperdamagelib.util.InvincibleHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class DecayEntityMethods {
    private DecayEntityMethods() {
    }

    public static float getTrueHealth(LivingEntity entity) {
        if (entity == null) return 0.0F;
        if (InvincibleHelper.isInvincible(entity) || (entity instanceof IDecayEntity decay && decay.isSuperInvincible())) {
            if (entity instanceof IDecayEntity decay) {
                return decay.getInvincibleHealthValue();
            }
            return entity.getHealth();
        }
        if (entity instanceof MeasurementDummyEntity dummy) {
            return dummy.isRemoveBypass() ? 0.0F : dummy.getMaxHealth();
        }
        if (DecayDamageUtil.FORCE_DAMAGE.get()) {
            return -Float.MAX_VALUE;
        }
        float maxHp = entity.getMaxHealth();
        if (Float.isNaN(maxHp) || maxHp <= 0.0F) maxHp = 20.0F;
        float decayAmount = (entity instanceof IDecayEntity decay) ? decay.getDecayAmount() : 0.0F;
        if (decayAmount >= maxHp) {
            return -Float.MAX_VALUE;
        }
        float rawHealth = getRawEntityDataHealth(entity);
        float cappedMax = Math.max(0.0F, maxHp - decayAmount);
        return Math.max(0.0F, Math.min(rawHealth, cappedMax));
    }

    public static boolean isReallyAlive(Entity entity) {
        if (entity == null) return false;
        if (InvincibleHelper.isInvincible(entity) || (entity instanceof IDecayEntity decay && decay.isSuperInvincible())) {
            return !InvincibleHelper.isRemoveBypass(entity);
        }
        if (entity instanceof MeasurementDummyEntity dummy) {
            return !dummy.isRemoveBypass();
        }
        if (DecayDamageUtil.FORCE_DAMAGE.get()) {
            return false;
        }
        if (entity instanceof LivingEntity living) {
            return getTrueHealth(living) > 0.0F;
        }
        return entity.getRemovalReason() == null;
    }

    public static boolean isReallyDeadOrDying(LivingEntity entity) {
        if (entity == null) return true;
        if (InvincibleHelper.isInvincible(entity) || (entity instanceof IDecayEntity decay && decay.isSuperInvincible())) {
            return false;
        }
        if (entity instanceof MeasurementDummyEntity dummy) {
            return dummy.isRemoveBypass();
        }
        if (DecayDamageUtil.FORCE_DAMAGE.get()) {
            return true;
        }
        return getTrueHealth(entity) <= 0.0F;
    }

    public static boolean isReallyRemoved(Entity entity) {
        if (entity == null) return true;
        if (InvincibleHelper.isInvincible(entity) || (entity instanceof IDecayEntity decay && decay.isSuperInvincible())) {
            return InvincibleHelper.isRemoveBypass(entity);
        }
        if (entity instanceof MeasurementDummyEntity dummy) {
            return dummy.isRemoveBypass();
        }
        if (DecayDamageUtil.FORCE_DAMAGE.get()) {
            return true;
        }
        return entity.getRemovalReason() != null;
    }

    public static float sanitizeHookHealth(Object hookInstance, float incomingHealth, Object targetEntity, Object phase) {
        if (targetEntity instanceof LivingEntity living) {
            return getTrueHealth(living);
        }
        return incomingHealth;
    }

    public static boolean sanitizeHookAlive(Object hookInstance, boolean incomingBool, Object targetEntity, Object phase) {
        if (targetEntity instanceof Entity entity) {
            return isReallyAlive(entity);
        }
        return incomingBool;
    }

    public static boolean sanitizeHookDeadOrDying(Object hookInstance, boolean incomingBool, Object targetEntity, Object phase) {
        if (targetEntity instanceof LivingEntity living) {
            return isReallyDeadOrDying(living);
        }
        return incomingBool;
    }

    public static boolean sanitizeHookRemoved(Object hookInstance, boolean incomingBool, Object targetEntity, Object phase) {
        if (targetEntity instanceof Entity entity) {
            return isReallyRemoved(entity);
        }
        return incomingBool;
    }

    public static float sanitizeStaticHealth(float incomingHealth, LivingEntity entity) {
        return getTrueHealth(entity);
    }

    public static boolean sanitizeStaticDeadOrDying(boolean incomingBool, LivingEntity entity) {
        return isReallyDeadOrDying(entity);
    }

    public static boolean sanitizeStaticAlive(boolean incomingBool, Entity entity) {
        return isReallyAlive(entity);
    }

    public static boolean shouldReplaceHealthMethod(Entity entity) {
        if (DecayDamageUtil.BYPASS_DECAY.get()) return false;
        if (entity == null) return false;
        if (InvincibleHelper.isInvincible(entity) || (entity instanceof IDecayEntity decay && decay.isSuperInvincible()))
            return true;
        if (entity instanceof MeasurementDummyEntity) return true;
        if (DecayDamageUtil.FORCE_DAMAGE.get()) return true;
        if (entity instanceof IDecayEntity decay && decay.getDecayAmount() > 0.0F) return true;
        return false;
    }

    public static float replaceGetHealth(LivingEntity entity) {
        return getTrueHealth(entity);
    }

    public static boolean replaceIsAlive(Entity entity) {
        return isReallyAlive(entity);
    }

    public static boolean replaceIsDeadOrDying(Entity entity) {
        if (entity instanceof LivingEntity living) return isReallyDeadOrDying(living);
        return false;
    }

    public static boolean shouldReplaceIsPickable(Entity entity) {
        return isProtected(entity);
    }

    public static boolean replaceIsPickable(Entity entity) {
        return !isProtected(entity);
    }

    public static boolean shouldReplaceIsAttackable(Entity entity) {
        return isProtected(entity);
    }

    public static boolean replaceIsAttackable(Entity entity) {
        return !isProtected(entity);
    }

    public static boolean shouldReplaceCanBeHitByProjectile(Entity entity) {
        return isProtected(entity);
    }

    public static boolean replaceCanBeHitByProjectile(Entity entity) {
        return !isProtected(entity);
    }

    public static boolean shouldInterceptSetPos(Entity entity, double x, double y, double z) {
        if (isProtected(entity)) {
            return Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z) ||
                    Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z) ||
                    Math.abs(x) > 29999984.0D || Math.abs(z) > 29999984.0D || Math.abs(y) > 20000000.0D;
        }
        return false;
    }

    public static boolean shouldInterceptRemoval(Entity entity, Entity.RemovalReason reason) {
        if (entity == null || reason == null) return false;
        if (InvincibleHelper.isRemoveBypass(entity)) return false;
        if (entity instanceof MeasurementDummyEntity dummy && dummy.isRemoveBypass()) return false;
        return isProtected(entity);
    }

    public static boolean shouldInterceptKill(Entity entity) {
        if (entity == null) return false;
        if (InvincibleHelper.isRemoveBypass(entity)) return false;
        if (entity instanceof MeasurementDummyEntity dummy && dummy.isRemoveBypass()) return false;
        return isProtected(entity);
    }

    public static boolean shouldInterceptTickListRemove(Entity entity) {
        if (entity == null) return false;
        if (InvincibleHelper.isRemoveBypass(entity)) return false;
        if (entity instanceof MeasurementDummyEntity dummy && dummy.isRemoveBypass()) return false;
        return isProtected(entity);
    }

    public static boolean shouldInterceptLookupRemove(Object obj) {
        if (obj instanceof Entity entity) {
            if (InvincibleHelper.isRemoveBypass(entity)) return false;
            if (entity instanceof MeasurementDummyEntity dummy && dummy.isRemoveBypass()) return false;
            return isProtected(entity);
        }
        return false;
    }

    private static boolean isProtected(Entity entity) {
        if (entity == null) return false;
        return InvincibleHelper.isInvincible(entity) ||
                (entity instanceof IDecayEntity decay && decay.isSuperInvincible()) ||
                (entity instanceof MeasurementDummyEntity dummy && !dummy.isRemoveBypass());
    }

    private static float getRawEntityDataHealth(LivingEntity entity) {
        try {
            Float val = entity.getEntityData().get(LivingEntityAccessor.getDataHealthId());
            return (val != null && !Float.isNaN(val)) ? val : entity.getMaxHealth();
        } catch (Throwable t) {
            return entity.getMaxHealth();
        }
    }
}