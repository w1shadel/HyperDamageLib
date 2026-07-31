package com.maxwell.hyperdamagelib.transformer;

import com.maxwell.hyperdamagelib.effect.DecayMobEffect;
import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.maxwell.hyperdamagelib.util.DecayDamageUtil;
import com.maxwell.hyperdamagelib.util.DecayUnsafeHelper;
import com.maxwell.hyperdamagelib.util.IDecayEntity;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class DecayEntityMethods {
    private static final ThreadLocal<java.util.List<MobEffectInstance>> PRESERVED_EFFECTS = ThreadLocal.withInitial(java.util.ArrayList::new);

    public static boolean shouldPreventEffectRemoval(MobEffect effect) {
        
        return effect instanceof DecayMobEffect;
    }

    public static void preserveDecayEffects(LivingEntity entity) {
        
        if (entity.level().isClientSide()) return;
        java.util.List<MobEffectInstance> list = PRESERVED_EFFECTS.get();
        list.clear();
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            if (instance.getEffect() instanceof DecayMobEffect) {
                list.add(new MobEffectInstance(instance));
            }
        }
    }

    public static void restoreDecayEffects(LivingEntity entity) {
        
        if (entity.level().isClientSide()) return;
        java.util.List<MobEffectInstance> list = PRESERVED_EFFECTS.get();
        if (!list.isEmpty()) {
            for (MobEffectInstance instance : list) {
                DecayDamageUtil.forceAddEffect(entity, instance, null);
            }
            list.clear();
        }
    }

    public static float handleSetHealth(LivingEntity entity, float health) {
        
        if (entity instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            return decay.getInvincibleHealthValue();
        }
        if (!entity.level().isClientSide()) {
            float currentHealth = entity.getHealth();
            if (health > currentHealth) {
                MobEffectInstance sickness = entity.getEffect(com.maxwell.hyperdamagelib.init.ModEffects.HEALING_SICKNESS.get());
                if (sickness != null) {
                    int amp = sickness.getAmplifier();
                    float reduction = 0.3F + (amp * 0.3F);
                    if (reduction > 0.9F) reduction = 0.9F;
                    float originalHeal = health - currentHealth;
                    float calculatedHeal = originalHeal * (1.0F - reduction);
                    health = currentHealth + calculatedHeal;
                }
            }
        }
        return health;
    }

    public static boolean shouldReplaceHealthMethod(Entity entity) {
        
        if (com.maxwell.hyperdamagelib.util.DecayDamageUtil.BYPASS_DECAY.get()) {
            return true;
        }

        if (entity instanceof LivingEntity living) {
            float rawHealth = living.getEntityData().get(com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor.getDataHealthId());



            float maxHealth = (float) living.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (rawHealth < maxHealth) {
                return true;
            }

            if (living.dead || living.deathTime > 0) {
                return true;
            }

            if (living instanceof IDecayEntity decay) {
                return decay.isSuperInvincible() || decay.getDecayAmount() > 0.0F;
            }
        }
        return false;
    }

    public static boolean handleForceAddEffect(LivingEntity entity, MobEffectInstance effect, Entity source) {
        if (DecayDamageUtil.BYPASS_EFFECT.get()) {
            forceAddEffectDirect(entity, effect, source);
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static void forceAddEffectDirect(LivingEntity entity, MobEffectInstance effect, Entity source) {
        
        if (entity.level().isClientSide()) return;
        java.util.Map<MobEffect, MobEffectInstance> activeEffects = null;
        try {
            java.lang.reflect.Field activeEffectsField = null;
            try {
                activeEffectsField = LivingEntity.class.getDeclaredField("activeEffects");
            } catch (NoSuchFieldException e) {
                try {
                    activeEffectsField = LivingEntity.class.getDeclaredField("f_20970_");
                } catch (NoSuchFieldException ex) {
                    for (java.lang.reflect.Field f : LivingEntity.class.getDeclaredFields()) {
                        if (java.util.Map.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            activeEffects = (java.util.Map<MobEffect, MobEffectInstance>) f.get(entity);
                            break;
                        }
                    }
                }
            }
            if (activeEffectsField != null) {
                com.maxwell.hyperdamagelib.util.DecayUnsafeHelper.forceSetAccessible(activeEffectsField);
                activeEffects = (java.util.Map<MobEffect, MobEffectInstance>) activeEffectsField.get(entity);
            }
        } catch (Throwable t) {
            com.maxwell.hyperdamagelib.HDL.LOGGER.error("[HDL] Failed to access activeEffects field", t);
        }
        if (activeEffects == null) return;
        MobEffect type = effect.getEffect();
        MobEffectInstance oldEffect = activeEffects.get(type);
        if (entity instanceof com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor accessor) {
            if (oldEffect == null) {
                activeEffects.put(type, effect);
                accessor.invokeOnEffectAdded(effect, source);
            } else {
                oldEffect.update(effect);
                accessor.invokeOnEffectUpdated(oldEffect, true, source);
            }
        }
        if (entity instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundUpdateMobEffectPacket(entity.getId(), effect));
        }
        if (entity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            ClientboundUpdateMobEffectPacket syncPacket = new ClientboundUpdateMobEffectPacket(entity.getId(), effect);
            serverLevel.getChunkSource().broadcastAndSend(entity, syncPacket);
        }
        try {
            java.lang.reflect.Field dirtyField = null;
            try {
                dirtyField = LivingEntity.class.getDeclaredField("effectsDirty");
            } catch (NoSuchFieldException e) {
                try {
                    dirtyField = LivingEntity.class.getDeclaredField("f_20973_");
                } catch (NoSuchFieldException ex) {
                    for (java.lang.reflect.Field f : LivingEntity.class.getDeclaredFields()) {
                        if (f.getType() == boolean.class && f.getName().equals("effectsDirty")) {
                            dirtyField = f;
                            break;
                        }
                    }
                }
            }
            if (dirtyField != null) {
                com.maxwell.hyperdamagelib.util.DecayUnsafeHelper.forceSetAccessible(dirtyField);
                dirtyField.setBoolean(entity, true);
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean handleForceDamage(LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (target instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            return true;
        }
        if (source.is(com.maxwell.hyperdamagelib.init.ModDamageTypes.PENETRATE) ||
                source.is(com.maxwell.hyperdamagelib.init.ModDamageTypes.EROSION) ||
                source.is(com.maxwell.hyperdamagelib.init.ModDamageTypes.VOID_SHRED) ||
                DecayDamageUtil.shouldApplyBypass(source)) {
            executeAbsoluteHurt(target, source, amount);
            return true;
        }
        return false;
    }

    private static void executeAbsoluteHurt(LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount) {
        
        if (target.level().isClientSide()) return;
        if (target.isDeadOrDying()) return;
        com.maxwell.hyperdamagelib.mixin.accessor.EntityAccessor entAcc = (com.maxwell.hyperdamagelib.mixin.accessor.EntityAccessor) target;
        com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor livAcc = (com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor) target;
        if (target.isSleeping()) {
            target.stopSleeping();
        }
        int invulnerableTime = entAcc.getInvulnerableTime();
        float lastHurt = livAcc.getLastHurt();
        float damageToApply = 0.0F;
        boolean isFreshAttack = false;
        if (invulnerableTime > 10) {
            if (amount > lastHurt) {
                damageToApply = amount - lastHurt;
                livAcc.setLastHurt(amount);
                isFreshAttack = true;
                com.maxwell.hyperdamagelib.HDL.LOGGER.info("[HDL-DEBUG] Mid-iframe higher damage. Apply diff: {}", damageToApply);
            } else {
                com.maxwell.hyperdamagelib.HDL.LOGGER.info("[HDL-DEBUG] Blocked by iframe.");
            }
        } else {
            damageToApply = amount;
            livAcc.setLastHurt(amount);
            entAcc.setInvulnerableTime(20);
            isFreshAttack = true;
            target.hurtTime = 10;
            com.maxwell.hyperdamagelib.HDL.LOGGER.info("[HDL-DEBUG] Fresh attack. Apply full: {}, set invTime to 20", damageToApply);
        }
        if (!isFreshAttack || damageToApply <= 0.0F) {
            return;
        }
        try {
            com.maxwell.hyperdamagelib.util.DecayDamageUtil.BYPASS_DECAY.set(true);
            float currentHealth = target.getEntityData().get(com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor.getDataHealthId());
            float nextHealth = Math.max(0.0F, currentHealth - damageToApply);
            com.maxwell.hyperdamagelib.HDL.LOGGER.info("[HDL-DEBUG] Real Server HP reduction: {} -> {}", currentHealth, nextHealth);
            if (source.is(com.maxwell.hyperdamagelib.init.ModDamageTypes.PENETRATE)) {
            } else if (source.is(com.maxwell.hyperdamagelib.init.ModDamageTypes.EROSION)) {
                if (target instanceof IDecayEntity decayTarget) {
                    decayTarget.addDecayAmount(damageToApply);
                }
            } else if (source.is(com.maxwell.hyperdamagelib.init.ModDamageTypes.VOID_SHRED)) {
                if (target instanceof IDecayEntity decayTarget) {
                    float afterArmor = net.minecraft.world.damagesource.CombatRules.getDamageAfterAbsorb(
                            damageToApply,
                            (float) target.getArmorValue(),
                            (float) target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS)
                    );
                    float afterMagic = afterArmor;
                    try {
                        afterMagic = livAcc.invokeGetDamageAfterMagicAbsorb(source, afterArmor);
                    } catch (Throwable ignored) {
                    }
                    float finalDamage = Math.max(1.0F, afterMagic);
                    float resistanceModifier = 1.0F;
                    if (target.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE)) {
                        int amp = target.getEffect(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE).getAmplifier();
                        resistanceModifier = 1.0F - (amp + 1) * 0.20F;
                    }
                    resistanceModifier = Math.max(0.10F, resistanceModifier);
                    damageToApply = finalDamage * resistanceModifier;
                    damageToApply = Math.max(0.0F, damageToApply);
                    nextHealth = Math.max(0.0F, currentHealth - damageToApply);
                    decayTarget.addDecayAmount(damageToApply);
                }
            } else if (DecayDamageUtil.shouldApplyBypass(source)) {
                if (target instanceof IDecayEntity decayTarget) {
                    decayTarget.addDecayAmount(damageToApply);
                }
            }
            target.setHealth(nextHealth);
            target.level().broadcastDamageEvent(target, source);
            target.markHurt();
            Entity attacker = source.getEntity();
            if (attacker instanceof LivingEntity livingAttacker) {
                target.setLastHurtByMob(livingAttacker);
                if (livingAttacker instanceof Player player) {
                    livAcc.setLastHurtByPlayer(player);
                    livAcc.setLastHurtByPlayerTime(100);
                }
            }
            if (attacker != null) {
                double d0 = attacker.getX() - target.getX();
                double d1;
                for (d1 = attacker.getZ() - target.getZ(); d0 * d0 + d1 * d1 < 1.0E-4D; d1 = (Math.random() - Math.random()) * 0.01D) {
                    d0 = (Math.random() - Math.random()) * 0.01D;
                }
                target.knockback(0.4D, d0, d1);
            }
            if (target.isDeadOrDying()) {
                boolean hasTotem = false;
                try {
                    hasTotem = livAcc.invokeCheckTotemDeathProtection(source);
                } catch (Throwable ignored) {
                }
                if (!hasTotem) {
                    target.die(source);
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
            com.maxwell.hyperdamagelib.util.DecayDamageUtil.BYPASS_DECAY.remove();
        }
    }

    public static boolean handleForceActuallyHurt(LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount) {
        
        if (target instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            return true;
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
        
        if (entity instanceof LivingEntity living) {
            float rawHealth = living.getEntityData().get(com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor.getDataHealthId());
            if (rawHealth <= 0.0F) {
                return true;
            }
            if (living instanceof IDecayEntity decay) {
                if (decay.isSuperInvincible()) return false;
                return decay.getDecayAmount() >= living.getMaxHealth();
            }
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
                boolean decayAllowedToDie = decay.getDecayAmount() >= living.getMaxHealth();
                return !decayAllowedToDie;
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
        


        float rawHealth = livingEntity.getEntityData().get(com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor.getDataHealthId());
        if (rawHealth <= 0.0F) {
            return 0.0F;
        }

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
    public static boolean shouldPreventRemovalWrite(Entity entity) {
        if (entity instanceof IDecayEntity decay) {
            return decay.isSuperInvincible();
        }
        return false;
    }

    public static boolean handleForceDie(LivingEntity self, net.minecraft.world.damagesource.DamageSource source) {
        
        if (com.maxwell.hyperdamagelib.util.DecayDamageUtil.BYPASS_DECAY.get()) {
            float rawHealth = self.getEntityData().get(com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor.getDataHealthId());
            if (rawHealth <= 0.0F) {
                executeForceDieSequence(self, source);
                return true;
            }
        }
        if (self instanceof IDecayEntity decay) {
            if (decay.getDecayAmount() >= self.getMaxHealth() && !decay.isSuperInvincible()) {
                executeForceDieSequence(self, source);
                return true;
            }
        }
        return false;
    }

    private static void executeForceDieSequence(LivingEntity self, net.minecraft.world.damagesource.DamageSource source) {
        
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