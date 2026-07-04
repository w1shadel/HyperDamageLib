package com.maxwell.hyperdamagelib.util;

import com.maxwell.hyperdamagelib.HDL;
import com.maxwell.hyperdamagelib.item.ErosionSwordItem;
import com.maxwell.hyperdamagelib.network.ModMessages;
import com.maxwell.hyperdamagelib.network.client.ClientboundDecaySyncPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = HDL.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DecayEventHandler {
    private static long EVENT_CANCELED_OFFSET = -1;
    private static java.lang.reflect.Field EVENT_CANCELED_FIELD = null;

    static {
        try {
            EVENT_CANCELED_FIELD = net.minecraftforge.eventbus.api.Event.class.getDeclaredField("isCanceled");
            DecayUnsafeHelper.forceSetAccessible(EVENT_CANCELED_FIELD);
            EVENT_CANCELED_OFFSET = DecayUnsafeHelper.getFieldOffset(EVENT_CANCELED_FIELD);
        } catch (Throwable ignored) {
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onPlayerAttack(AttackEntityEvent event) {
        if (event.getTarget() instanceof IDecayEntity decayTarget && event.getTarget() instanceof LivingEntity livingTarget) {
            Player attacker = event.getEntity();
            ItemStack heldItem = attacker.getMainHandItem();
            if (heldItem.getItem() instanceof ErosionSwordItem) {
                float maxHp = livingTarget.getMaxHealth();
                if (maxHp >= 3.0E38F || Float.isInfinite(maxHp) || Float.isNaN(maxHp)) {
                    com.maxwell.hyperdamagelib.util.DecayForceKillHelper.decayForceKill(livingTarget);
                    livingTarget.level().broadcastEntityEvent(livingTarget, (byte) 2);
                    return;
                }
                livingTarget.level().broadcastEntityEvent(livingTarget, (byte) 2);
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

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void enforceDecayDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof IDecayEntity decayEntity) {
            if (decayEntity.getDecayAmount() >= event.getEntity().getMaxHealth()) {
                if (event.isCanceled()) {
                    try {
                        event.setCanceled(false);
                    } catch (Throwable ignored) {
                    }
                    if (event.isCanceled() && EVENT_CANCELED_OFFSET != -1) {
                        DecayUnsafeHelper.putBoolean(event, EVENT_CANCELED_OFFSET, false);
                    }
                }
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
                player.unsetRemoved();
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
                    new ClientboundDecaySyncPacket(event.getTarget().getId(), decay.getDecayAmount(), decay.isSuperInvincible(), decay.isKeepCurrentHealth(), decay.getInvincibleHealthValue())
            );
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer && serverPlayer instanceof IDecayEntity decay) {
            ModMessages.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new ClientboundDecaySyncPacket(serverPlayer.getId(), decay.getDecayAmount(), decay.isSuperInvincible(), decay.isKeepCurrentHealth(), decay.getInvincibleHealthValue())
            );
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(net.minecraftforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(
                net.minecraft.commands.Commands.literal("hdl")
                        .then(net.minecraft.commands.Commands.literal("inspect")
                                .executes(context -> {
                                    net.minecraft.commands.CommandSourceStack source = context.getSource();
                                    net.minecraft.server.level.ServerPlayer player = source.getPlayer();
                                    if (player != null) {
                                        performInspection(player);
                                    }
                                    return 1;
                                })
                        )
        );
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
        boolean hasNoSugarErased = false;
        try {
            for (java.lang.reflect.Field field : player.getClass().getDeclaredFields()) {
                if (field.getName().toLowerCase().contains("erase")) {
                    if (field.getType() == boolean.class) {
                        field.setAccessible(true);
                        hasNoSugarErased = field.getBoolean(player);
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
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§fNoSugar Erased: " + (hasNoSugarErased ? "§cTRUE" : "§aFALSE")));
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§d================================"));
    }
}