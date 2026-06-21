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
            float cappedHealth = livingEntity.getMaxHealth() - decay.getDecayAmount();
            return Math.max(-Float.MAX_VALUE, cappedHealth);
        }
        return livingEntity.getMaxHealth();
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

    public static boolean handleForceDamage(LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (target instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            return true;
        }

        if (DecayDamageUtil.shouldApplyBypass(source)) {
            if (target instanceof IDecayEntity decayTarget) {
                decayTarget.addDecayAmount(amount);

                if (!target.level().isClientSide()) {
                    target.level().broadcastEntityEvent(target, (byte) 2);
                    net.minecraft.world.entity.Entity attacker = source.getEntity();
                    if (attacker instanceof LivingEntity livingAttacker) {
                        double xDiff = target.getX() - livingAttacker.getX();
                        double zDiff = target.getZ() - livingAttacker.getZ();
                        double knockbackStrength = 0.5D;
                        knockbackStrength += livingAttacker.getAttributeValue(
                                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_KNOCKBACK
                        );
                        int enchantmentLevel = net.minecraft.world.item.enchantment.EnchantmentHelper.getKnockbackBonus(livingAttacker);
                        if (enchantmentLevel > 0) {
                            knockbackStrength += (double) enchantmentLevel * 0.5D;
                        }
                        if (livingAttacker.isSprinting()) {
                            knockbackStrength += 0.5D;
                        }

                        target.knockback(knockbackStrength, xDiff, zDiff);
                        target.hurtMarked = true;
                    }
                }

                return true;
            }
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
        if (source.is(com.maxwell.hyperdamagelib.init.ModDamageTypes.VOID_SHRED)) {
            if (target instanceof com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor accessor) {
                float afterArmor = accessor.invokeGetDamageAfterArmorAbsorb(source, amount);
                float afterMagic = accessor.invokeGetDamageAfterMagicAbsorb(source, afterArmor);
                float finalDamage = Math.max(1.0F, afterMagic);
                float absorption = target.getAbsorptionAmount();
                if (absorption > 0.0F) {
                    float absorptionDamage = Math.min(absorption, finalDamage);
                    target.setAbsorptionAmount(absorption - absorptionDamage);
                    finalDamage -= absorptionDamage;
                }
                if (finalDamage > 0.0F) {
                    target.getCombatTracker().recordDamage(source, finalDamage);
                    target.setHealth(target.getHealth() - finalDamage);
                }
                return true;
            }
        }
        if (DecayDamageUtil.shouldApplyBypass(source)) {
            if (target instanceof IDecayEntity decayTarget) {
                decayTarget.subtractTrueHP(amount);
                return true;
            }
        }
        return false;
    }

    public static float handleSetHealth(LivingEntity entity, float health) {
        if (entity instanceof IDecayEntity decay) {
            if (decay.isSuperInvincible()) {
                return decay.getInvincibleHealthValue();
            }
            float maxHealth = entity.getMaxHealth();
            if (Float.isInfinite(maxHealth) || Float.isNaN(maxHealth)) {
                maxHealth = Float.MAX_VALUE;
            }
            float decayAmount = decay.getDecayAmount();
            if (Float.isInfinite(decayAmount) || Float.isNaN(decayAmount)) {
                decayAmount = maxHealth;
            }
            float cappedHealth = Math.max(0.0F, maxHealth - decayAmount);

            if (Float.isNaN(health) || health > cappedHealth) {
                return cappedHealth;
            }
            return health;
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