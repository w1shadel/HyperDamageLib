package com.maxwell.hyperdamagelib.transformer;

import com.maxwell.hyperdamagelib.entity.MeasurementDummyEntity;
import com.maxwell.hyperdamagelib.init.ModDamageTypes;
import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.maxwell.hyperdamagelib.util.DecayDamageUtil;
import com.maxwell.hyperdamagelib.util.InvincibleHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Consumer;

public class DecayEntityMethods {

    public static boolean shouldReplaceHealthMethod(Entity entity) {
        if (DecayDamageUtil.BYPASS_DECAY.get()) return false;
        if (entity instanceof MeasurementDummyEntity) return true;
        return InvincibleHelper.isInvincible(entity);
    }

    public static float replaceGetHealth(LivingEntity livingEntity) {
        if (livingEntity instanceof MeasurementDummyEntity dummy) {
            return dummy.isRemoveBypass() ? 0.0F : dummy.getMaxHealth();
        }
        if (InvincibleHelper.isInvincible(livingEntity)) {
            float maxHealth = (float) livingEntity.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH
            );
            return Float.isNaN(maxHealth) || maxHealth < 20.0F ? 20.0F : maxHealth;
        }

        try {
            Float rawHealth = livingEntity.getEntityData().get(LivingEntityAccessor.getDataHealthId());
            return rawHealth != null ? rawHealth : 20.0F;
        } catch (Throwable t) {
            return 20.0F;
        }
    }

    public static float getHealth(float health, LivingEntity livingEntity) {
        if (livingEntity instanceof MeasurementDummyEntity dummy) {
            return dummy.isRemoveBypass() ? 0.0F : dummy.getMaxHealth();
        }
        if (InvincibleHelper.isInvincible(livingEntity)) {
            float maxHealth = (float) livingEntity.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH
            );
            return Float.isNaN(maxHealth) || maxHealth < 20.0F ? 20.0F : maxHealth;
        }
        return health;
    }
    public static boolean handleForceDamage(LivingEntity target, DamageSource source, float amount) {
        if (source.is(ModDamageTypes.EROSION) || source.is(ModDamageTypes.PENETRATE)) {
            DecayDamageUtil.applyCustomDamage(target, source, amount);
            return true;
        }
        return false;
    }
    public static boolean replaceIsDeadOrDying(Entity entity) {
        if (entity instanceof MeasurementDummyEntity dummy) {
            return dummy.isRemoveBypass();
        }
        if (InvincibleHelper.isInvincible(entity)) {
            return false;
        }
        return false;
    }

    public static boolean isDeadOrDying(boolean deadOrDying, LivingEntity livingEntity) {
        if (livingEntity instanceof MeasurementDummyEntity dummy) {
            return dummy.isRemoveBypass() && deadOrDying;
        }
        if (InvincibleHelper.isInvincible(livingEntity)) {
            return false;
        }
        return deadOrDying;
    }

    public static boolean replaceIsAlive(Entity entity) {
        return !replaceIsDeadOrDying(entity);
    }

    public static boolean isAlive(boolean alive, Entity entity) {
        if (entity instanceof MeasurementDummyEntity dummy) {
            return !dummy.isRemoveBypass() || alive;
        }
        if (InvincibleHelper.isInvincible(entity)) {
            return true;
        }
        return alive;
    }

    public static Entity.RemovalReason getRemovalReason(Entity.RemovalReason removalReason, Entity entity) {
        if (entity instanceof MeasurementDummyEntity dummy && !dummy.isRemoveBypass()) {
            return null;
        }
        if (InvincibleHelper.isInvincible(entity)) {
            return null;
        }
        return removalReason;
    }

    public static boolean isRemoved(boolean removed, Entity entity) {
        if (entity instanceof MeasurementDummyEntity dummy && !dummy.isRemoveBypass()) {
            return false;
        }
        if (InvincibleHelper.isInvincible(entity)) {
            return false;
        }
        return removed;
    }

    public static boolean shouldReplaceIsPickable(Entity entity) {
        return InvincibleHelper.isInvincible(entity);
    }

    public static boolean replaceIsPickable(Entity entity) {
        return false;
    }

    public static boolean shouldOverrideTick(Entity entity) {
        return InvincibleHelper.isInvincible(entity);
    }

    public static void tickOverride(Consumer<Entity> consumer, Entity entity) {
        consumer.accept(entity);
    }
}