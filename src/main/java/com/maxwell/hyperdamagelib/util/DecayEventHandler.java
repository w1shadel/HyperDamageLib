package com.maxwell.hyperdamagelib.util;

import com.maxwell.hyperdamagelib.HDL;
import com.maxwell.hyperdamagelib.network.ModMessages;
import com.maxwell.hyperdamagelib.network.client.ClientboundDecaySyncPacket;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = HDL.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DecayEventHandler {
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onLivingAttack(LivingAttackEvent event) {
        net.minecraft.world.damagesource.DamageSource source = event.getSource();
        if (source.is(com.maxwell.hyperdamagelib.init.ModDamageTypes.PENETRATE)) {
            return;
        }
        net.minecraft.world.entity.Entity attacker = source.getEntity();
        LivingEntity victim = event.getEntity();
        if (attacker instanceof LivingEntity livingAttacker) {
            ItemStack heldItem = livingAttacker.getMainHandItem();
            if (heldItem.getItem() instanceof com.maxwell.hyperdamagelib.item.PenetrateSwordItem) {
                event.setCanceled(true);
                if (!livingAttacker.level().isClientSide()) {
                    com.maxwell.hyperdamagelib.HDL.LOGGER.info("[HDL-DEBUG] onLivingAttack intercepted. Swung PenetrateSword.");
                    float originalDamage = event.getAmount();
                    float damage = originalDamage > 0 ? originalDamage : 18.0F;
                    String customMessage = "%victim% was pierced through the chest by %attacker%'s Absolute Thrust!";
                    net.minecraft.world.damagesource.DamageSource penetrateSource =
                            DecayDamageUtil.getPenetrateSource(livingAttacker.level(), livingAttacker, customMessage);
                    victim.hurt(penetrateSource, damage);
                    net.minecraft.world.effect.MobEffectInstance activeSickness = victim.getEffect(com.maxwell.hyperdamagelib.init.ModEffects.HEALING_SICKNESS.get());
                    int nextAmp = 0;
                    if (activeSickness != null) {
                        nextAmp = Math.min(2, activeSickness.getAmplifier() + 1);
                    }
                    net.minecraft.world.effect.MobEffectInstance sicknessInstance = new net.minecraft.world.effect.MobEffectInstance(
                            com.maxwell.hyperdamagelib.init.ModEffects.HEALING_SICKNESS.get(), 160, nextAmp
                    );
                    sicknessInstance.setCurativeItems(java.util.List.of());

                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onLivingHeal(net.minecraftforge.event.entity.living.LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (entity instanceof IDecayEntity decay) {
            if (decay.getDecayHoldTicks() > 0 || decay.getDecayAmount() > 0.0F) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingTickInvincibleSafety(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (entity instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            double minHeight = entity.level().getMinBuildHeight() - 32.0D;
            if (entity.getY() < minHeight) {
                if (entity instanceof ServerPlayer player) {
                    net.minecraft.core.BlockPos respawnPos = player.getRespawnPosition();
                    if (respawnPos == null) {
                        respawnPos = player.level().getSharedSpawnPos();
                    }
                    player.teleportTo(
                            player.server.getLevel(player.getRespawnDimension()),
                            respawnPos.getX() + 0.5D,
                            respawnPos.getY() + 1.0D,
                            respawnPos.getZ() + 0.5D,
                            player.getYRot(),
                            player.getXRot()
                    );
                    player.displayClientMessage(Component.translatable("message.hyperdamagelib.void_fall"), true);
                } else {
                    net.minecraft.core.BlockPos sharedSpawn = entity.level().getSharedSpawnPos();
                    entity.teleportTo(sharedSpawn.getX() + 0.5D, sharedSpawn.getY() + 1.0D, sharedSpawn.getZ() + 0.5D);
                }
                entity.fallDistance = 0.0F;
            }
        }
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer) {
            if (serverPlayer.connection == null) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTickSafetySalvage(net.minecraftforge.event.TickEvent.PlayerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide()) return;
        if (player instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            if (player.isRemoved()) {
                player.setPose(net.minecraft.world.entity.Pose.STANDING);
                player.dead = false;
                player.deathTime = 0;
            }
            float invincibleHealth = decay.getInvincibleHealthValue();
            Float currentSynchedHealth = player.getEntityData().get(com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor.getDataHealthId());
            if (currentSynchedHealth == null || currentSynchedHealth < 1.0f) {
                try {
                    com.maxwell.hyperdamagelib.util.DecayDamageUtil.BYPASS_DECAY.set(true);
                    player.getEntityData().set(com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor.getDataHealthId(), invincibleHealth);
                } finally {
                    com.maxwell.hyperdamagelib.util.DecayDamageUtil.BYPASS_DECAY.remove();
                }
            }
            if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                if (serverLevel.getEntity(player.getId()) == null) {
                    try {
                        serverLevel.addDuringPortalTeleport((net.minecraft.server.level.ServerPlayer) player);
                    } catch (Throwable ignored) {
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (entity instanceof ServerPlayer player && player.connection == null) {
            return;
        }
        if (entity instanceof IDecayEntity decay) {
            int hold = decay.getDecayHoldTicks();
            float currentDecay = decay.getDecayAmount();
            if (currentDecay > 0.0f) {
                float maxHealth = entity.getMaxHealth();
                float baseDecreaseRate = entity instanceof Player ? maxHealth * 0.0005F : maxHealth * 0.0025F;
                float regenBoost = 0.0F;
                if (entity.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION)) {
                    int amp = entity.getEffect(net.minecraft.world.effect.MobEffects.REGENERATION).getAmplifier();
                    regenBoost = 1.0F + (amp + 1) * 0.10F;
                }
                float finalDecrease = 0.0F;
                if (hold > 0) {
                    decay.setDecayHoldTicks(hold - 1);
                    if (regenBoost > 0.0F) {
                        finalDecrease = baseDecreaseRate * regenBoost;
                    }
                } else {
                    if (regenBoost > 0.0F) {
                        finalDecrease = baseDecreaseRate * regenBoost;
                    } else {
                        finalDecrease = baseDecreaseRate;
                    }
                }
                if (finalDecrease > 0.0F) {
                    decay.setDecayAmount(Math.max(0.0f, currentDecay - finalDecrease));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onItemUseFinish(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide()) return;
        if (entity instanceof IDecayEntity decay && event.getItem().isEdible()) {
            net.minecraft.world.food.FoodProperties food = event.getItem().getItem().getFoodProperties();
            if (food != null) {
                int nutrition = food.getNutrition();
                float decayReduction = entity.getMaxHealth() * (nutrition * 0.025F);
                float currentDecay = decay.getDecayAmount();
                if (currentDecay > 0.0F) {
                    decay.setDecayAmount(Math.max(0.0f, currentDecay - decayReduction));
                }

            }
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player newPlayer = event.getEntity();
        if (newPlayer instanceof IDecayEntity decayEntity) {
            decayEntity.setDecayAmount(0.0f);
            decayEntity.setSuperInvincible(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void preventInvincibleHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.getEntity() instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            event.setCanceled(true);
            event.setAmount(0.0F);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void preventInvincibleAttack(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (event.getEntity() instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void preventInvincibleDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void preventInvincibleDamage(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (event.getEntity() instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            event.setCanceled(true);
            event.setAmount(0.0F);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof IDecayEntity decay && event.getEntity() instanceof ServerPlayer serverPlayer) {
            ModMessages.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new ClientboundDecaySyncPacket(event.getTarget().getId(), decay.getDecayAmount(), decay.isSuperInvincible(), decay.isKeepCurrentHealth(), decay.getInvincibleHealthValue(), decay.isHealBlocked())
            );
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer && serverPlayer instanceof IDecayEntity decay) {
            ModMessages.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new ClientboundDecaySyncPacket(serverPlayer.getId(), decay.getDecayAmount(), decay.isSuperInvincible(), decay.isKeepCurrentHealth(), decay.getInvincibleHealthValue(), decay.isHealBlocked())
            );
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("hdl")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("inspect")
                                .executes(context -> {
                                    net.minecraft.commands.CommandSourceStack source = context.getSource();
                                    net.minecraft.server.level.ServerPlayer player = source.getPlayer();
                                    if (player != null) {
                                        performInspection(player);
                                    }
                                    return 1;
                                })
                        )
                        .then(Commands.literal("forceDamage")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0.0F))
                                                .executes(context -> {
                                                    return forceDamage(context.getSource(), EntityArgument.getEntities(context, "targets"), FloatArgumentType.getFloat(context, "amount"), null);
                                                })
                                                .then(Commands.argument("attacker", EntityArgument.entity())
                                                        .executes(context -> {
                                                            return forceDamage(context.getSource(), EntityArgument.getEntities(context, "targets"), FloatArgumentType.getFloat(context, "amount"), EntityArgument.getEntity(context, "attacker"));
                                                        })
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("forceHeal")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(context -> {
                                            return forceHeal(context.getSource(), EntityArgument.getEntities(context, "targets"), null);
                                        })
                                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0.0F))
                                                .executes(context -> {
                                                    return forceHeal(context.getSource(), EntityArgument.getEntities(context, "targets"), FloatArgumentType.getFloat(context, "amount"));
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("setHyperInvincible")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(context -> {
                                                    return setHyperInvincible(context.getSource(), EntityArgument.getEntities(context, "targets"), BoolArgumentType.getBool(context, "value"));
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("setDecayAmount")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0.0F))
                                                .executes(context -> {
                                                    return setDecayAmount(context.getSource(), EntityArgument.getEntities(context, "targets"), FloatArgumentType.getFloat(context, "amount"));
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("setHealBlock")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(context -> {
                                                    return setHealBlock(context.getSource(), EntityArgument.getEntities(context, "targets"), BoolArgumentType.getBool(context, "value"));
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("forceEffect")
                                .then(Commands.literal("give")
                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                .then(Commands.argument("effect", ResourceLocationArgument.id())
                                                        .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggestResource(ForgeRegistries.MOB_EFFECTS.getKeys(), builder))
                                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                                                .then(Commands.argument("amplifier", IntegerArgumentType.integer(0))
                                                                        .then(Commands.argument("showParticles", BoolArgumentType.bool())
                                                                                .executes(context -> {
                                                                                    ResourceLocation effectId = ResourceLocationArgument.getId(context, "effect");
                                                                                    MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(effectId);
                                                                                    if (effect == null) {
                                                                                        throw new SimpleCommandExceptionType(Component.translatable("commands.hdl.force_effect.invalid", effectId)).create();
                                                                                    }
                                                                                    return forceEffectGive(context.getSource(), EntityArgument.getEntities(context, "targets"), effect, IntegerArgumentType.getInteger(context, "seconds"), IntegerArgumentType.getInteger(context, "amplifier"), BoolArgumentType.getBool(context, "showParticles"));
                                                                                })
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                                .then(Commands.literal("clear")
                                        .then(Commands.argument("targets", EntityArgument.entities())
                                                .then(Commands.argument("effect", ResourceLocationArgument.id())
                                                        .suggests((ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggestResource(ForgeRegistries.MOB_EFFECTS.getKeys(), builder))
                                                        .executes(context -> {
                                                            ResourceLocation effectId = ResourceLocationArgument.getId(context, "effect");
                                                            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(effectId);
                                                            if (effect == null) {
                                                                throw new SimpleCommandExceptionType(Component.translatable("commands.hdl.force_effect.invalid", effectId)).create();
                                                            }
                                                            return forceEffectClear(context.getSource(), EntityArgument.getEntities(context, "targets"), effect);
                                                        })
                                                )
                                        )
                                )
                        )
        );
    }

    private static int forceDamage(net.minecraft.commands.CommandSourceStack source, java.util.Collection<? extends net.minecraft.world.entity.Entity> targets, float amount, @Nullable net.minecraft.world.entity.Entity attacker) {
        int count = 0;
        try {
            DecayDamageUtil.FORCE_DAMAGE.set(true);
            for (net.minecraft.world.entity.Entity entity : targets) {
                if (entity instanceof LivingEntity living) {
                    net.minecraft.world.damagesource.DamageSource damageSource = DecayDamageUtil.getErosionSource(living.level(), attacker);
                    living.hurt(damageSource, amount);
                    count++;
                }
            }
        } finally {
            DecayDamageUtil.FORCE_DAMAGE.remove();
        }
        final int finalCount = count;
        source.sendSuccess(() -> Component.translatable("commands.hdl.force_damage.success", finalCount, amount), true);
        return count;
    }

    private static int forceHeal(net.minecraft.commands.CommandSourceStack source, java.util.Collection<? extends net.minecraft.world.entity.Entity> targets, @Nullable Float amount) {
        int count = 0;
        for (net.minecraft.world.entity.Entity entity : targets) {
            if (entity instanceof LivingEntity living) {
                executeForceHeal(living, amount);
                count++;
            }
        }
        final int finalCount = count;
        source.sendSuccess(() -> {
            Component amountComp = (amount == null)
                    ? Component.translatable("commands.hdl.common.full")
                    : Component.literal(String.valueOf(amount));
            return Component.translatable("commands.hdl.force_heal.success", finalCount, amountComp);
        }, true);
        return count;
    }

    private static void executeForceHeal(LivingEntity target, @Nullable Float amount) {
        try {
            DecayDamageUtil.BYPASS_DECAY.set(true);
            if (target instanceof IDecayEntity decay) {
                decay.setDecayAmount(0.0F);
                decay.setDecayHoldTicks(0);
            }
            float originalMax = (float) target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            float targetHealth;
            if (amount == null) {
                targetHealth = originalMax;
            } else {
                targetHealth = Math.min(originalMax, target.getHealth() + amount);
            }
            target.setHealth(targetHealth);
            target.deathTime = 0;
            target.dead = false;
        } finally {
            DecayDamageUtil.BYPASS_DECAY.remove();
        }
    }

    private static int setHyperInvincible(net.minecraft.commands.CommandSourceStack source, java.util.Collection<? extends net.minecraft.world.entity.Entity> targets, boolean value) {
        int count = 0;
        for (net.minecraft.world.entity.Entity entity : targets) {
            if (entity instanceof IDecayEntity decay) {
                decay.setSuperInvincible(value);
                count++;
            }
        }
        final int finalCount = count;
        source.sendSuccess(() -> {
            Component stateComp = value
                    ? Component.translatable("commands.hdl.common.on")
                    : Component.translatable("commands.hdl.common.off");
            return Component.translatable("commands.hdl.set_hyper_invincible.success", finalCount, stateComp);
        }, true);
        return count;
    }

    private static int setDecayAmount(net.minecraft.commands.CommandSourceStack source, java.util.Collection<? extends net.minecraft.world.entity.Entity> targets, float amount) {
        int count = 0;
        for (net.minecraft.world.entity.Entity entity : targets) {
            if (entity instanceof IDecayEntity decay) {
                decay.setDecayAmount(amount);
                count++;
            }
        }
        final int finalCount = count;
        source.sendSuccess(() -> Component.translatable("commands.hdl.set_decay_amount.success", finalCount, amount), true);
        return count;
    }

    private static int setHealBlock(net.minecraft.commands.CommandSourceStack source, java.util.Collection<? extends net.minecraft.world.entity.Entity> targets, boolean value) {
        int count = 0;
        for (net.minecraft.world.entity.Entity entity : targets) {
            if (entity instanceof IDecayEntity decay) {
                decay.setHealBlocked(value);
                count++;
            }
        }
        final int finalCount = count;
        source.sendSuccess(() -> {
            Component stateComp = value
                    ? Component.translatable("commands.hdl.common.on")
                    : Component.translatable("commands.hdl.common.off");
            return Component.translatable("commands.hdl.set_heal_block.success", finalCount, stateComp);
        }, true);
        return count;
    }

    private static int forceEffectGive(net.minecraft.commands.CommandSourceStack source, java.util.Collection<? extends net.minecraft.world.entity.Entity> targets, MobEffect effect, int seconds, int amplifier, boolean showParticles) {
        int count = 0;
        int durationTicks = seconds * 20;
        MobEffectInstance instance = new MobEffectInstance(effect, durationTicks, amplifier, false, showParticles);
        for (net.minecraft.world.entity.Entity entity : targets) {
            if (entity instanceof LivingEntity living) {
                DecayDamageUtil.forceAddEffect(living, instance, null);
                count++;
            }
        }
        final int finalCount = count;
        source.sendSuccess(() -> Component.translatable("commands.hdl.force_effect.give.success", finalCount, effect.getDisplayName()), true);
        return count;
    }

    private static int forceEffectClear(net.minecraft.commands.CommandSourceStack source, java.util.Collection<? extends net.minecraft.world.entity.Entity> targets, MobEffect effect) {
        int count = 0;
        try {
            com.maxwell.hyperdamagelib.util.DecayDamageUtil.BYPASS_EFFECT.set(true);
            for (net.minecraft.world.entity.Entity entity : targets) {
                if (entity instanceof LivingEntity living) {
                    living.removeEffect(effect);
                    count++;
                }
            }
        } finally {
            com.maxwell.hyperdamagelib.util.DecayDamageUtil.BYPASS_EFFECT.remove();
        }
        final int finalCount = count;
        source.sendSuccess(() -> Component.translatable("commands.hdl.force_effect.clear.success", finalCount, effect.getDisplayName()), true);
        return count;
    }

    private static void performInspection(net.minecraft.server.level.ServerPlayer player) {
        if (player == null) return;
        com.maxwell.hyperdamagelib.util.IDecayEntity decay = (com.maxwell.hyperdamagelib.util.IDecayEntity) player;
        boolean isInvincible = decay.isSuperInvincible();
        float getHealth = player.getHealth();
        float getMaxHealth = player.getMaxHealth();
        boolean isAlive = player.isAlive();
        boolean isDeadOrDying = player.isDeadOrDying();
        Float synchedHealth = player.getEntityData().get(com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor.getDataHealthId());
        boolean rawDead = player.dead;
        int deathTime = player.deathTime;
        boolean isRemoved = player.isRemoved();
        net.minecraft.world.entity.Entity.RemovalReason removalReason = player.getRemovalReason();
        boolean isAddedToWorld = player.isAddedToWorld();
        boolean hasErased = false;
        try {
            for (java.lang.reflect.Field field : player.getClass().getDeclaredFields()) {
                if (field.getName().toLowerCase().contains("erase")) {
                    if (field.getType() == boolean.class) {
                        field.setAccessible(true);
                        hasErased = field.getBoolean(player);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§d==== HDL Player Diagnostics ===="));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§f[HDL] Invincible Mode : " + (isInvincible ? "§aON" : "§cOFF")));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§fHP (getHealth()): " + getHealth + " / " + getMaxHealth));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§fHP (EntityData): " + (synchedHealth != null ? synchedHealth : "null")));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§fisAlive(): " + (isAlive ? "§aTRUE" : "§cFALSE")));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§fisDeadOrDying(): " + (isDeadOrDying ? "§cTRUE" : "§aFALSE")));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§fdead (Field): " + (rawDead ? "§cTRUE" : "§aFALSE") + " | deathTime: " + deathTime));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§fisRemoved(): " + isRemoved + " | Reason: " + removalReason));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§fisAddedToWorld: " + isAddedToWorld));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§f Erased: " + (hasErased ? "§cTRUE" : "§aFALSE")));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§d================================"));
    }

    @SubscribeEvent
    public static void onServerTickDummyWatchdog(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (java.util.Map.Entry<UUID, com.maxwell.hyperdamagelib.util.DummyWatchdog.DummyData> entry : com.maxwell.hyperdamagelib.util.DummyWatchdog.ACTIVE_DUMMIES.entrySet()) {
            UUID uuid = entry.getKey();
            com.maxwell.hyperdamagelib.util.DummyWatchdog.DummyData data = entry.getValue();
            net.minecraft.server.level.ServerLevel world = server.getLevel(data.dimension);
            if (world == null) continue;
            net.minecraft.world.entity.Entity entity = world.getEntity(uuid);
            boolean needsReconstruction = false;
            if (entity == null) {
                needsReconstruction = true;
            } else {
                if (entity.isRemoved()) {
                    needsReconstruction = true;
                } else if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
                    boolean isDead = ((com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor) living).isDeadFlag();
                    if (living.getHealth() < living.getMaxHealth() - 100.0F ||
                            living.getPose() == net.minecraft.world.entity.Pose.DYING ||
                            isDead) {
                        needsReconstruction = true;
                    }
                }
            }
            if (needsReconstruction) {
                if (entity instanceof com.maxwell.hyperdamagelib.entity.MeasurementDummyEntity dummy) {
                    dummy.setRemoveBypass(true);
                    dummy.discard();
                }
                com.maxwell.hyperdamagelib.util.DecayForceKillHelper.removeFromMemory(entity != null ? entity : world.getEntity(uuid));
                com.maxwell.hyperdamagelib.entity.MeasurementDummyEntity newDummy = com.maxwell.hyperdamagelib.init.ModEntities.MEASUREMENT_DUMMY.get().create(world);
                if (newDummy != null) {
                    newDummy.setUUID(data.uuid);
                    newDummy.moveTo(data.x, data.y, data.z, data.yRot, data.xRot);
                    for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                        if (slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.ARMOR) {
                            newDummy.setItemSlot(slot, data.armor.get(slot.getIndex()).copy());
                        } else {
                            newDummy.setItemSlot(slot, data.hands.get(slot.getIndex()).copy());
                        }
                    }
                    newDummy.setRemoveBypass(false);
                    boolean success = world.addFreshEntity(newDummy);
                    if (!success) {
                        UUID fallbackUuid = UUID.randomUUID();
                        newDummy.setUUID(fallbackUuid);
                        world.addFreshEntity(newDummy);
                        com.maxwell.hyperdamagelib.util.DummyWatchdog.ACTIVE_DUMMIES.remove(data.uuid);
                        com.maxwell.hyperdamagelib.util.DummyWatchdog.ACTIVE_DUMMIES.put(fallbackUuid,
                                new com.maxwell.hyperdamagelib.util.DummyWatchdog.DummyData(
                                        fallbackUuid, data.dimension, data.x, data.y, data.z, data.yRot, data.xRot, data.armor, data.hands
                                )
                        );
                        com.maxwell.hyperdamagelib.HDL.LOGGER.info("[HDL Watchdog] Dummy (UUID: {}) assigned to fallback UUID: {} for forced respawn.", data.uuid, fallbackUuid);
                    } else {
                        com.maxwell.hyperdamagelib.HDL.LOGGER.info("[HDL Watchdog] Measurement Dummy (UUID: {}) was automatically restored.", uuid);
                    }
                }
            }
        }
    }
}