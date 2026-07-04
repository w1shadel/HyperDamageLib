package com.maxwell.hyperdamagelib.transformer;

import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.maxwell.hyperdamagelib.util.DecayDamageUtil;
import com.maxwell.hyperdamagelib.util.IDecayEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class DecayEntityMethods {
    public static boolean shouldReplaceHealthMethod(Entity entity) {
        if (entity instanceof LivingEntity living && entity instanceof IDecayEntity decay) {
            return decay.isSuperInvincible() || decay.getDecayAmount() > 0.0F;
        }
        return false;
    }

    public static float replaceGetHealth(LivingEntity livingEntity) {
        if (livingEntity instanceof IDecayEntity decay) {
            if (decay.isSuperInvincible()) {
                return decay.getInvincibleHealthValue();
            }
            float rawHealth = livingEntity.getEntityData().get(LivingEntityAccessor.getDataHealthId());
            float originalMax = (float) livingEntity.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH
            );
            float cappedMax = Math.max(0.0F, originalMax - decay.getDecayAmount());
            return Math.max(-Float.MAX_VALUE, Math.min(rawHealth, cappedMax));
        }
        return (float) livingEntity.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH
        );
    }

    public static float getHealth(float health, LivingEntity livingEntity) {
        if (livingEntity instanceof IDecayEntity decay) {
            if (decay.isSuperInvincible()) {
                return decay.getInvincibleHealthValue();
            }
            float decayAmount = decay.getDecayAmount();
            if (decayAmount > 0.0F) {
                float cappedHealth = livingEntity.getMaxHealth() - decayAmount;
                return Math.min(health, cappedHealth);
            }
        }
        return health;
    }

    public static boolean replaceIsDeadOrDying(Entity entity) {
        if (entity instanceof LivingEntity living && entity instanceof IDecayEntity decay) {
            if (decay.isSuperInvincible()) return false;
            return decay.getDecayAmount() >= living.getMaxHealth();
        }
        return false;
    }

    public static boolean isDeadOrDying(boolean deadOrDying, LivingEntity livingEntity) {
        if (livingEntity instanceof IDecayEntity decay) {
            if (decay.isSuperInvincible()) return false;
            if (decay.getDecayAmount() >= livingEntity.getMaxHealth()) {
                return true;
            }
        }
        return deadOrDying;
    }

    public static boolean replaceIsAlive(Entity entity) {
        return !replaceIsDeadOrDying(entity);
    }

    public static boolean isAlive(boolean alive, Entity entity) {
        if (entity instanceof LivingEntity living && entity instanceof IDecayEntity decay) {
            if (decay.isSuperInvincible()) return true;
            if (decay.getDecayAmount() >= living.getMaxHealth()) {
                return false;
            }
        }
        return alive;
    }

    public static Entity.RemovalReason getRemovalReason(Entity.RemovalReason removalReason, Entity entity) {
        if (entity instanceof IDecayEntity decay && decay.isSuperInvincible() && !decay.isRemoveBypass()) {
            return null;
        }
        return removalReason;
    }

    public static boolean isRemoved(boolean removed, Entity entity) {
        if (entity instanceof IDecayEntity decay && decay.isSuperInvincible() && !decay.isRemoveBypass()) {
            return false;
        }
        return removed;
    }

    public static boolean shouldReplaceIsPickable(Entity entity) {
        return entity instanceof IDecayEntity decay && decay.isSuperInvincible();
    }

    public static boolean replaceIsPickable(Entity entity) {
        return false;
    }

    public static boolean shouldOverrideTick(Entity entity) {
        return entity instanceof IDecayEntity decay && decay.isSuperInvincible();
    }

    public static void tickOverride(java.util.function.Consumer<Entity> consumer, Entity entity) {
        consumer.accept(entity);
    }

    public static void updateLastTicks(net.minecraft.server.level.ServerLevel serverLevel) {
    }

    public static boolean shouldPreventTeleport(Entity entity) {
        if (entity instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            return isForcedByRivalMod();
        }
        return false;
    }

    public static boolean shouldPreventRespawn(Entity entity) {
        if (entity instanceof LivingEntity living && entity instanceof IDecayEntity decay) {
            if (decay.isSuperInvincible()) {
                boolean cspAllowedToDie = decay.getDecayAmount() >= living.getMaxHealth();
                return !cspAllowedToDie;
            }
        }
        return false;
    }

    public static boolean shouldPreventServerPlayerDie(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            if (player.connection == null) {
                return true;
            }
            if (player instanceof IDecayEntity decay && decay.isSuperInvincible()) {
                return true;
            }
        }
        return false;
    }

    public static float getMaxHealth(float maxHealth, LivingEntity livingEntity) {
        if (livingEntity instanceof IDecayEntity decay) {
            if (decay.isSuperInvincible()) {
                return maxHealth;
            }
            float decayAmount = decay.getDecayAmount();
            if (decayAmount > 0.0F) {
                return Math.max(1.0F, maxHealth - decayAmount);
            }
        }
        return maxHealth;
    }

    public static float replaceGetMaxHealth(LivingEntity livingEntity) {
        if (livingEntity instanceof IDecayEntity decay) {
            float originalMax = (float) livingEntity.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH
            );
            if (decay.isSuperInvincible()) {
                return originalMax;
            }
            float cappedHealth = originalMax - decay.getDecayAmount();
            return Math.max(1.0F, cappedHealth);
        }
        return (float) livingEntity.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH
        );
    }

    public static boolean handleForceDamage(LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (target instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            return true;
        }
        if (source.is(com.maxwell.hyperdamagelib.init.ModDamageTypes.EROSION) ||
                source.is(com.maxwell.hyperdamagelib.init.ModDamageTypes.VOID_SHRED) ||
                DecayDamageUtil.shouldApplyBypass(source)) {
            float maxHp = target.getMaxHealth();
            if (maxHp >= 3.0E38F || Float.isInfinite(maxHp) || Float.isNaN(maxHp)) {
                com.maxwell.hyperdamagelib.util.DecayForceKillHelper.decayForceKill(target);
                return true;
            }
            return handleForceActuallyHurt(target, source, amount);
        }
        return false;
    }

    public static boolean shouldPreventRemovalWrite(Entity entity) {
        if (entity instanceof IDecayEntity decay) {
            return decay.isSuperInvincible();
        }
        return false;
    }

    public static boolean handleForceDie(LivingEntity self, net.minecraft.world.damagesource.DamageSource source) {
        if (self instanceof IDecayEntity decay) {
            if (decay.getDecayAmount() >= self.getMaxHealth() && !decay.isSuperInvincible()) {
                if (!self.level().isClientSide()) {
                    ((LivingEntityAccessor) self).setDeadFlag(true);
                    self.deathTime = 1;
                    ((LivingEntityAccessor) self).invokeDropAllDeathLoot(source);
                    if (self instanceof ServerPlayer player) {
                        player.awardStat(net.minecraft.stats.Stats.DEATHS);
                        player.getCombatTracker().recheckStatus();
                        net.minecraft.network.chat.Component deathMsg = player.getCombatTracker().getDeathMessage();
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket(player.getId(), deathMsg));
                    }
                }
                self.level().broadcastEntityEvent(self, (byte) 3);
                return true;
            }
        }
        return false;
    }

    public static boolean handleForceActuallyHurt(LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (target instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            return true;
        }
        if (source.is(com.maxwell.hyperdamagelib.init.ModDamageTypes.EROSION)) {
            if (target instanceof IDecayEntity decayTarget) {
                decayTarget.addDecayAmount(amount);
                float nextHealth = target.getHealth() - amount;
                target.setHealth(nextHealth);
                if (nextHealth <= 0.0F || decayTarget.getDecayAmount() >= target.getMaxHealth()) {
                    target.die(source);
                }
                return true;
            }
        }
        if (source.is(com.maxwell.hyperdamagelib.init.ModDamageTypes.VOID_SHRED)) {
            if (target instanceof IDecayEntity decayTarget) {
                float afterArmor = net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(
                        amount,
                        (float) target.getArmorValue(),
                        (float) target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS)
                );
                float afterMagic = afterArmor;
                try {
                    if (target instanceof com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor accessor) {
                        afterMagic = accessor.invokeGetDamageAfterMagicAbsorb(source, afterArmor);
                    }
                } catch (Throwable ignored) {
                }
                float finalDamage = Math.max(1.0F, afterMagic);
                float resistanceModifier = 1.0F;
                if (target.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE)) {
                    int amp = target.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE).getAmplifier();
                    resistanceModifier = 1.0F - (amp + 1) * 0.20F;
                }
                resistanceModifier = Math.max(0.10F, resistanceModifier);
                finalDamage = finalDamage * resistanceModifier;
                finalDamage = Math.max(0.0F, finalDamage);
                decayTarget.addDecayAmount(finalDamage);
                float nextHealth = target.getHealth() - finalDamage;
                target.setHealth(nextHealth);
                if (nextHealth <= 0.0F || decayTarget.getDecayAmount() >= target.getMaxHealth()) {
                    target.die(source);
                }
                return true;
            }
        }
        if (DecayDamageUtil.shouldApplyBypass(source)) {
            if (target instanceof IDecayEntity decayTarget) {
                decayTarget.addDecayAmount(amount);
                float nextHealth = target.getHealth() - amount;
                target.setHealth(nextHealth);
                if (nextHealth <= 0.0F || decayTarget.getDecayAmount() >= target.getMaxHealth()) {
                    target.die(source);
                }
                return true;
            }
        }
        return false;
    }

    public static float handleSetHealth(LivingEntity entity, float health) {
        if (entity instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            return decay.getInvincibleHealthValue();
        }
        return health;
    }

    public static boolean handleForceKill(Entity entity) {
        if (entity instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            return true;
        }
        return false;
    }

    public static boolean handleForceDropLoot(LivingEntity entity) {
        if (entity instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            return true;
        }
        return false;
    }

    private static boolean isForcedByRivalMod() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            String name = element.getClassName();
            if (name.startsWith("java.") || name.startsWith("javax.") ||
                    name.startsWith("sun.") || name.startsWith("com.sun.") ||
                    name.startsWith("jdk.") || name.startsWith("org.lwjgl.") ||
                    name.startsWith("cpw.mods.") || name.startsWith("net.minecraftforge.") ||
                    name.startsWith("net.minecraft.") || name.startsWith("org.spongepowered.") ||
                    name.contains("com.maxwell") || name.startsWith("com.mojang.") ||
                    name.startsWith("com.google.") || name.startsWith("org.apache.") ||
                    name.startsWith("io.netty.")) {
                continue;
            }
            return true;
        }
        return false;
    }
}