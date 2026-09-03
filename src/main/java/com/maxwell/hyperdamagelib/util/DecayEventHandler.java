package com.maxwell.hyperdamagelib.util;

import com.maxwell.hyperdamagelib.HDL;
import com.maxwell.hyperdamagelib.init.ModEntities;
import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.maxwell.hyperdamagelib.network.ModMessages;
import com.maxwell.hyperdamagelib.network.client.ClientboundDecaySyncPacket;
import com.maxwell.hyperdamagelib.transformer.DecayEntityMethods;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = HDL.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DecayEventHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onLivingHurtSecurity(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (InvincibleHelper.isDummy(entity)) return;
        if (InvincibleHelper.isInvincible(entity)) {
            event.setCanceled(true);
            event.setAmount(0.0F);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onLivingAttackSecurity(LivingAttackEvent event) {
        LivingEntity entity = event.getEntity();
        if (InvincibleHelper.isDummy(entity)) return;
        if (InvincibleHelper.isInvincible(entity)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onLivingDamageSecurity(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (InvincibleHelper.isDummy(entity)) return;
        if (InvincibleHelper.isInvincible(entity)) {
            event.setCanceled(true);
            event.setAmount(0.0F);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onLivingDeathSecurity(LivingDeathEvent event) {
        if (InvincibleHelper.isInvincible(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onLivingHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (InvincibleHelper.isHealBlocked(entity)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        InvincibleHelper.SERVER_REMOVE_BYPASS.remove(uuid);
        InvincibleHelper.CLIENT_REMOVE_BYPASS.remove(uuid);
    }

    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event) {
        InvincibleHelper.SERVER_REMOVE_BYPASS.clear();
        InvincibleHelper.CLIENT_REMOVE_BYPASS.clear();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player newPlayer = event.getEntity();
        InvincibleHelper.setInvincible(newPlayer, false);
        InvincibleHelper.setHealBlocked(newPlayer, false);
        InvincibleHelper.keepAlive(newPlayer);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            InvincibleHelper.setInvincible(player, false);
            InvincibleHelper.setHealBlocked(player, false);
            InvincibleHelper.keepAlive(player);
            syncDecayState(player);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (entity instanceof ServerPlayer player && player.connection == null) return;

        if (InvincibleHelper.isInvincible(entity)) {
            double minHeight = entity.level().getMinBuildHeight() - 32.0D;
            if (entity.getY() < minHeight) {
                teleportToSafePosition(entity);
            }
            InvincibleHelper.keepAlive(entity);
        }
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncDecayState(player);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof LivingEntity living && event.getEntity() instanceof ServerPlayer tracker) {
            if (living instanceof IDecayEntity decay) {
                ModMessages.INSTANCE.send(
                        PacketDistributor.PLAYER.with(() -> tracker),
                        new ClientboundDecaySyncPacket(living.getId(), decay.isSuperInvincible(), decay.isKeepCurrentHealth(), decay.getInvincibleHealthValue(), decay.isHealBlocked())
                );
            }
        }
    }

    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer && serverPlayer.connection == null) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
                Commands.literal("hdl")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("inspect")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayer();
                                    if (player != null) performInspection(player);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("forceDamage")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0.0F))
                                                .executes(ctx -> forceDamage(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"), FloatArgumentType.getFloat(ctx, "amount"), null))
                                                .then(Commands.argument("attacker", EntityArgument.entity())
                                                        .executes(ctx -> forceDamage(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"), FloatArgumentType.getFloat(ctx, "amount"), EntityArgument.getEntity(ctx, "attacker")))
                                                )
                                        )
                                )
                        )
                        .then(Commands.literal("forceHeal")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(ctx -> forceHeal(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"), null))
                                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0.0F))
                                                .executes(ctx -> forceHeal(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"), FloatArgumentType.getFloat(ctx, "amount")))
                                        )
                                )
                        )
                        .then(Commands.literal("setHyperInvincible")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setHyperInvincible(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"), BoolArgumentType.getBool(ctx, "value")))
                                        )
                                )
                        )
                        .then(Commands.literal("setHealBlock")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> setHealBlock(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"), BoolArgumentType.getBool(ctx, "value")))
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
                                                                                .executes(ctx -> {
                                                                                    ResourceLocation effectId = ResourceLocationArgument.getId(ctx, "effect");
                                                                                    MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(effectId);
                                                                                    if (effect == null) {
                                                                                        throw new SimpleCommandExceptionType(Component.translatable("commands.hdl.force_effect.invalid", effectId)).create();
                                                                                    }
                                                                                    return forceEffectGive(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"), effect, IntegerArgumentType.getInteger(ctx, "seconds"), IntegerArgumentType.getInteger(ctx, "amplifier"), BoolArgumentType.getBool(ctx, "showParticles"));
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
                                                        .executes(ctx -> {
                                                            ResourceLocation effectId = ResourceLocationArgument.getId(ctx, "effect");
                                                            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(effectId);
                                                            if (effect == null) {
                                                                throw new SimpleCommandExceptionType(Component.translatable("commands.hdl.force_effect.invalid", effectId)).create();
                                                            }
                                                            return forceEffectClear(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"), effect);
                                                        })
                                                )
                                        )
                                )
                        )
        );
    }

    private static int forceDamage(CommandSourceStack source, Collection<? extends Entity> targets, float amount, @Nullable Entity attacker) {
        int count = 0;
        try {
            DecayDamageUtil.FORCE_DAMAGE.set(true);
            for (Entity entity : targets) {
                if (entity instanceof LivingEntity living) {
                    DamageSource damageSource = DecayDamageUtil.getErosionSource(living.level(), attacker);
                    DecayDamageUtil.applyCustomDamage(living, damageSource, amount);
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

    private static int forceHeal(CommandSourceStack source, Collection<? extends Entity> targets, @Nullable Float amount) {
        int count = 0;
        for (Entity entity : targets) {
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
            float originalMax = (float) target.getAttributeValue(Attributes.MAX_HEALTH);
            float targetHealth = (amount == null) ? originalMax : Math.min(originalMax, target.getHealth() + amount);
            target.setHealth(targetHealth);
            InvincibleHelper.keepAlive(target);
        } finally {
            DecayDamageUtil.BYPASS_DECAY.remove();
        }
    }

    private static int setHyperInvincible(CommandSourceStack source, Collection<? extends Entity> targets, boolean value) {
        int count = 0;
        for (Entity entity : targets) {
            InvincibleHelper.setInvincible(entity, value);
            count++;
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

    private static int setHealBlock(CommandSourceStack source, Collection<? extends Entity> targets, boolean value) {
        int count = 0;
        for (Entity entity : targets) {
            InvincibleHelper.setHealBlocked(entity, value);
            count++;
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

    private static int forceEffectGive(CommandSourceStack source, Collection<? extends Entity> targets, MobEffect effect, int seconds, int amplifier, boolean showParticles) {
        int count = 0;
        int durationTicks = seconds * 20;
        MobEffectInstance instance = new MobEffectInstance(effect, durationTicks, amplifier, false, showParticles);
        for (Entity entity : targets) {
            if (entity instanceof LivingEntity living) {
                if (DecayDamageUtil.forceAddEffect(living, instance, null)) {
                    count++;
                }
            }
        }
        final int finalCount = count;
        source.sendSuccess(() -> Component.translatable("commands.hdl.force_effect.give.success", finalCount, effect.getDisplayName()), true);
        return count;
    }

    private static int forceEffectClear(CommandSourceStack source, Collection<? extends Entity> targets, MobEffect effect) {
        int count = 0;
        try {
            DecayDamageUtil.BYPASS_EFFECT.set(true);
            for (Entity entity : targets) {
                if (entity instanceof LivingEntity living) {
                    living.removeEffect(effect);
                    count++;
                }
            }
        } finally {
            DecayDamageUtil.BYPASS_EFFECT.remove();
        }
        final int finalCount = count;
        source.sendSuccess(() -> Component.translatable("commands.hdl.force_effect.clear.success", finalCount, effect.getDisplayName()), true);
        return count;
    }

    private static void syncDecayState(LivingEntity entity) {
        if (entity instanceof IDecayEntity decay && !entity.level().isClientSide()) {
            ModMessages.INSTANCE.send(
                    PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity),
                    new ClientboundDecaySyncPacket(
                            entity.getId(),
                            decay.isSuperInvincible(),
                            decay.isKeepCurrentHealth(),
                            decay.getInvincibleHealthValue(),
                            decay.isHealBlocked()
                    )
            );
        }
    }

    private static void teleportToSafePosition(LivingEntity entity) {
        if (entity instanceof ServerPlayer player) {
            BlockPos respawnPos = player.getRespawnPosition();
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
            BlockPos sharedSpawn = entity.level().getSharedSpawnPos();
            entity.teleportTo(sharedSpawn.getX() + 0.5D, sharedSpawn.getY() + 1.0D, sharedSpawn.getZ() + 0.5D);
        }
        entity.fallDistance = 0.0F;
    }

    private static void performInspection(ServerPlayer player) {
        if (player == null) return;
        float getHealthVal = player.getHealth();
        float maxHealthVal = player.getMaxHealth();
        float trueHealthVal = DecayEntityMethods.getTrueHealth(player);
        Float entityDataHealth = player.getEntityData().get(LivingEntityAccessor.getDataHealthId());
        boolean isAliveVal = player.isAlive();
        boolean isDeadOrDyingVal = player.isDeadOrDying();
        boolean isReallyAliveVal = DecayEntityMethods.isReallyAlive(player);
        boolean rawDeadField = player.dead;
        int deathTimeVal = player.deathTime;
        boolean isRemovedVal = player.isRemoved();

        player.sendSystemMessage(Component.literal("§d================ HDL Diagnostics ================"));
        player.sendSystemMessage(Component.literal("§e[Health Values]"));
        player.sendSystemMessage(Component.literal("  §7- player.getHealth(): §f" + getHealthVal + " / " + maxHealthVal));
        player.sendSystemMessage(Component.literal("  §7- DecayEntityMethods.getTrueHealth(): §a" + trueHealthVal));
        player.sendSystemMessage(Component.literal("  §7- EntityData (DATA_HEALTH_ID): §b" + entityDataHealth));
        player.sendSystemMessage(Component.literal("§e[State Values]"));
        player.sendSystemMessage(Component.literal("  §7- isAlive(): §f" + (isAliveVal ? "§aTRUE" : "§cFALSE") + " §7| isReallyAlive(): §f" + (isReallyAliveVal ? "§aTRUE" : "§cFALSE")));
        player.sendSystemMessage(Component.literal("  §7- isDeadOrDying(): §f" + (isDeadOrDyingVal ? "§cTRUE" : "§aFALSE") + " §7| dead(field): §f" + (rawDeadField ? "§cTRUE" : "§aFALSE") + " | deathTime: " + deathTimeVal));
        player.sendSystemMessage(Component.literal("  §7- isRemoved(): §f" + (isRemovedVal ? "§cTRUE" : "§aFALSE")));
        player.sendSystemMessage(Component.literal("  §7- isSuperInvincible(): §f" + (InvincibleHelper.isInvincible(player) ? "§aON" : "§cOFF")));
        player.sendSystemMessage(Component.literal("§d================================================"));
    }
}