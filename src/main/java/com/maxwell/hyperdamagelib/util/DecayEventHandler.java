package com.maxwell.hyperdamagelib.util;

import com.maxwell.hyperdamagelib.HDL;
import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.maxwell.hyperdamagelib.network.ModMessages;
import com.maxwell.hyperdamagelib.network.client.ClientboundDecaySyncPacket;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.Collection;

@Mod.EventBusSubscriber(modid = HDL.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DecayEventHandler {
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        InvincibleHelper.clearAllSessionData();
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        InvincibleHelper.clearAllSessionData();
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        Entity entity = event.getEntity();
        if (entity instanceof Player) return;
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            PurgedEntitiesSavedData data = PurgedEntitiesSavedData.get(serverLevel);
            if (data != null && data.isPurged(entity)) {
                event.setCanceled(true);
                if (entity instanceof LivingEntity living) {
                    DecayForceKillHelper.purgeBossBars(living, serverLevel);
                    DecayForceKillHelper.breakControllers(living);
                    DecayForceKillHelper.breakBrain(living);
                    DecayForceKillHelper.removeFromMemory(living);
                } else {
                    DecayForceKillHelper.removeFromMemory(entity);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        InvincibleHelper.SERVER_REMOVE_BYPASS.remove(event.getEntity().getUUID());
        InvincibleHelper.CLIENT_REMOVE_BYPASS.remove(event.getEntity().getUUID());
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
            if (!living.level().isClientSide()) {
                ModMessages.INSTANCE.send(
                        PacketDistributor.PLAYER.with(() -> tracker),
                        new ClientboundDecaySyncPacket(
                                living.getId(),
                                InvincibleHelper.isInvincible(living),
                                true,
                                InvincibleHelper.getInvincibleHealth(living),
                                InvincibleHelper.isHealBlocked(living)
                        )
                );
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player original = event.getOriginal();
        Player newPlayer = event.getEntity();
        InvincibleHelper.setRemoveBypass(original, true);
        boolean wasInvincible = InvincibleHelper.isInvincible(original);
        if (wasInvincible) {
            InvincibleHelper.setInvincible(newPlayer, true);
        }
        InvincibleHelper.keepAlive(newPlayer);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            InvincibleHelper.keepAlive(player);
            syncDecayState(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity == null) return;
        if (InvincibleHelper.isInvincible(entity)) {
            InvincibleHelper.keepAlive(entity);
            if (!entity.level().isClientSide()) {
                double y = entity.getY();
                if (Double.isNaN(y) || Double.isInfinite(y) || y < entity.level().getMinBuildHeight() - 32.0D) {
                    teleportToSafePosition(entity);
                }
            }
        }
    }

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
            InvincibleHelper.keepAlive(event.getEntity());
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
        for (Entity entity : targets) {
            if (entity instanceof LivingEntity living) {
                try (var ignored = DecayDamageUtil.forceKillScope(living)) {
                    DamageSource damageSource = DecayDamageUtil.getErosionSource(living.level(), attacker);
                    DecayDamageUtil.applyCustomDamage(living, damageSource, amount);
                    count++;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
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
        try (var ignored = DecayDamageUtil.bypassScope(target)) {
            float originalMax = (float) target.getAttributeValue(Attributes.MAX_HEALTH);
            Float rawHp = target.getEntityData().get(LivingEntityAccessor.getDataHealthId());
            float curHp = (rawHp != null && !Float.isNaN(rawHp)) ? rawHp : originalMax;
            float targetHealth = (amount == null) ? originalMax : Math.min(originalMax, curHp + amount);
            target.setHealth(targetHealth);
            InvincibleHelper.keepAlive(target);
        } catch (Exception e) {
            throw new RuntimeException(e);
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
        for (Entity entity : targets) {
            if (entity instanceof LivingEntity living) {
                try (var ignored = DecayDamageUtil.bypassEffectScope(living)) {
                    living.removeEffect(effect);
                    count++;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        final int finalCount = count;
        source.sendSuccess(() -> Component.translatable("commands.hdl.force_effect.clear.success", finalCount, effect.getDisplayName()), true);
        return count;
    }

    private static void syncDecayState(LivingEntity entity) {
        InvincibleHelper.syncToTracking(entity);
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
}