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
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
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
        if (InvincibleHelper.isInvincible(event.getEntity())) {
            event.setCanceled(true);
            event.setAmount(0.0F);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onLivingAttackSecurity(LivingAttackEvent event) {
        if (InvincibleHelper.isInvincible(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onLivingDamageSecurity(LivingDamageEvent event) {
        if (InvincibleHelper.isInvincible(event.getEntity())) {
            event.setCanceled(true);
            event.setAmount(0.0F);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onLivingDeathSecurity(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (InvincibleHelper.isInvincible(entity)) {
            event.setCanceled(true);
            return;
        }
        if (entity instanceof IDecayEntity decay) {
            float maxHp = entity.getMaxHealth();
            if (decay.getDecayAmount() >= maxHp && maxHp > 0.0F) {
                event.setCanceled(false);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onLivingHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (entity instanceof IDecayEntity decay) {
            if (decay.isHealBlocked() || decay.getDecayHoldTicks() > 0 || decay.getDecayAmount() > 0.0F) {
                event.setCanceled(true);
            }
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
        DummyWatchdog.ACTIVE_DUMMIES.clear();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        Player newPlayer = event.getEntity();
        if (newPlayer instanceof IDecayEntity decayEntity) {
            decayEntity.setDecayAmount(0.0F);
            decayEntity.setSuperInvincible(false);
            decayEntity.setHealBlocked(false);
            decayEntity.setDecayHoldTicks(0);
            decayEntity.setKeepCurrentHealth(false);
        }
        newPlayer.dead = false;
        newPlayer.deathTime = 0;
        newPlayer.setPose(Pose.STANDING);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (player instanceof IDecayEntity decay) {
                decay.setDecayAmount(0.0F);
                decay.setDecayHoldTicks(0);
                decay.setHealBlocked(false);
                decay.setSuperInvincible(false);
                decay.setKeepCurrentHealth(false);
            }
            syncDecayState(player);
        }
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        if (entity instanceof ServerPlayer player && player.connection == null) {
            return;
        }
        if (entity instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            double minHeight = entity.level().getMinBuildHeight() - 32.0D;
            if (entity.getY() < minHeight) {
                teleportToSafePosition(entity);
            }
            if (entity.dead || entity.deathTime > 0) {
                entity.dead = false;
                entity.deathTime = 0;
                entity.setPose(Pose.STANDING);
            }
            return;
        }
        if (entity instanceof IDecayEntity decay) {
            int hold = decay.getDecayHoldTicks();
            float currentDecay = decay.getDecayAmount();
            if (currentDecay > 0.0F) {
                float maxHealth = entity.getMaxHealth();
                float baseRate = entity instanceof Player ? maxHealth * 0.001F : maxHealth * 0.005F;
                float regenBoost = 0.0F;
                if (entity.hasEffect(MobEffects.REGENERATION)) {
                    int amp = entity.getEffect(MobEffects.REGENERATION).getAmplifier();
                    regenBoost = 1.0F + (amp + 1) * 0.25F;
                }
                if (hold > 0) {
                    decay.setDecayHoldTicks(hold - 1);
                } else {
                    float decrease = (regenBoost > 0.0F) ? (baseRate * regenBoost) : baseRate;
                    float nextDecay = Math.max(0.0F, currentDecay - decrease);
                    decay.setDecayAmount(nextDecay);
                    if (entity.tickCount % 20 == 0 || nextDecay == 0.0F) {
                        syncDecayState(entity);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onItemUseFinish(LivingEntityUseItemEvent.Finish event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || entity.level().isClientSide()) return;
        if (entity instanceof IDecayEntity decay && event.getItem().isEdible()) {
            FoodProperties food = event.getItem().getItem().getFoodProperties();
            if (food != null) {
                int nutrition = food.getNutrition();
                float reduction = entity.getMaxHealth() * (nutrition * 0.025F);
                float currentDecay = decay.getDecayAmount();
                if (currentDecay > 0.0F) {
                    decay.setDecayAmount(Math.max(0.0F, currentDecay - reduction));
                }
            }
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
                        new ClientboundDecaySyncPacket(living.getId(), decay.getDecayAmount(), decay.isSuperInvincible(), decay.isKeepCurrentHealth(), decay.getInvincibleHealthValue(), decay.isHealBlocked())
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
                        .then(Commands.literal("setDecayAmount")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0.0F))
                                                .executes(ctx -> setDecayAmount(ctx.getSource(), EntityArgument.getEntities(ctx, "targets"), FloatArgumentType.getFloat(ctx, "amount")))
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
            if (target instanceof IDecayEntity decay) {
                decay.setDecayAmount(0.0F);
                decay.setDecayHoldTicks(0);
            }
            float originalMax = (float) target.getAttributeValue(Attributes.MAX_HEALTH);
            float targetHealth = (amount == null) ? originalMax : Math.min(originalMax, target.getHealth() + amount);
            target.setHealth(targetHealth);
            target.deathTime = 0;
            target.dead = false;
        } finally {
            DecayDamageUtil.BYPASS_DECAY.remove();
        }
    }

    private static int setHyperInvincible(CommandSourceStack source, Collection<? extends Entity> targets, boolean value) {
        int count = 0;
        for (Entity entity : targets) {
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

    private static int setDecayAmount(CommandSourceStack source, Collection<? extends Entity> targets, float amount) {
        int count = 0;
        for (Entity entity : targets) {
            if (entity instanceof IDecayEntity decay) {
                decay.setDecayAmount(amount);
                count++;
            }
        }
        final int finalCount = count;
        source.sendSuccess(() -> Component.translatable("commands.hdl.set_decay_amount.success", finalCount, amount), true);
        return count;
    }

    private static int setHealBlock(CommandSourceStack source, Collection<? extends Entity> targets, boolean value) {
        int count = 0;
        for (Entity entity : targets) {
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
                            decay.getDecayAmount(),
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
        IDecayEntity decay = (IDecayEntity) player;
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
        boolean nsErased = false;
        boolean nsFullset = false;
        boolean nsForceHalo = false;
        float nsDelta = 0.0F;
        try {
            for (java.lang.reflect.Field f : player.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                String fn = f.getName().toLowerCase();
                if (fn.equals("erased")) nsErased = f.getBoolean(player);
                if (fn.equals("fullset")) nsFullset = f.getBoolean(player);
                if (fn.equals("forcehalo")) nsForceHalo = f.getBoolean(player);
                if (fn.equals("delta")) nsDelta = f.getFloat(player);
            }
        } catch (Throwable ignored) {
        }
        player.sendSystemMessage(Component.literal("§d================ HDL Diagnostics ================"));
        player.sendSystemMessage(Component.literal("§e[Health Values]"));
        player.sendSystemMessage(Component.literal("  §7- player.getHealth(): §f" + getHealthVal + " / " + maxHealthVal));
        player.sendSystemMessage(Component.literal("  §7- DecayEntityMethods.getTrueHealth(): §a" + trueHealthVal));
        player.sendSystemMessage(Component.literal("  §7- EntityData (DATA_HEALTH_ID): §b" + entityDataHealth));
        player.sendSystemMessage(Component.literal("  §7- DecayAmount: §c" + decay.getDecayAmount()));
        player.sendSystemMessage(Component.literal("§e[State Values]"));
        player.sendSystemMessage(Component.literal("  §7- isAlive(): §f" + (isAliveVal ? "§aTRUE" : "§cFALSE") + " §7| isReallyAlive(): §f" + (isReallyAliveVal ? "§aTRUE" : "§cFALSE")));
        player.sendSystemMessage(Component.literal("  §7- isDeadOrDying(): §f" + (isDeadOrDyingVal ? "§cTRUE" : "§aFALSE") + " §7| dead(field): §f" + (rawDeadField ? "§cTRUE" : "§aFALSE") + " | deathTime: " + deathTimeVal));
        player.sendSystemMessage(Component.literal("  §7- isRemoved(): §f" + (isRemovedVal ? "§cTRUE" : "§aFALSE")));
        player.sendSystemMessage(Component.literal("§e[NoSugar Foreign Internal State]"));
        player.sendSystemMessage(Component.literal("  §7- erased: §f" + (nsErased ? "§cTRUE (ERASED!)" : "§aFALSE")));
        player.sendSystemMessage(Component.literal("  §7- Fullset (SnackProtector): §f" + (nsFullset ? "§eACTIVE" : "§7OFF")));
        player.sendSystemMessage(Component.literal("  §7- ForceHalo: §f" + (nsForceHalo ? "§eACTIVE" : "§7OFF") + " | Delta: §f" + nsDelta));
        player.sendSystemMessage(Component.literal("§d================================================"));
    }

    @SubscribeEvent
    public static void onServerTickDummyWatchdog(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        for (Map.Entry<UUID, DummyWatchdog.DummyData> entry : DummyWatchdog.ACTIVE_DUMMIES.entrySet()) {
            UUID uuid = entry.getKey();
            DummyWatchdog.DummyData data = entry.getValue();
            ServerLevel world = server.getLevel(data.dimension);
            if (world == null) continue;
            Entity entity = world.getEntity(uuid);
            boolean needsReconstruction = false;
            if (entity == null || entity.isRemoved()) {
                needsReconstruction = true;
            } else if (entity instanceof LivingEntity living) {
                boolean isDead = ((LivingEntityAccessor) living).isDeadFlag();
                if (living.getHealth() < living.getMaxHealth() - 100.0F || living.getPose() == Pose.DYING || isDead) {
                    needsReconstruction = true;
                }
            }
            if (needsReconstruction) {
                if (entity instanceof com.maxwell.hyperdamagelib.entity.MeasurementDummyEntity dummy) {
                    dummy.setRemoveBypass(true);
                    dummy.discard();
                }
                DecayForceKillHelper.removeFromMemory(entity != null ? entity : world.getEntity(uuid));
                com.maxwell.hyperdamagelib.entity.MeasurementDummyEntity newDummy = ModEntities.MEASUREMENT_DUMMY.get().create(world);
                if (newDummy != null) {
                    newDummy.setUUID(data.uuid);
                    newDummy.moveTo(data.x, data.y, data.z, data.yRot, data.xRot);
                    for (EquipmentSlot slot : EquipmentSlot.values()) {
                        if (slot.getType() == EquipmentSlot.Type.ARMOR) {
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
                        DummyWatchdog.ACTIVE_DUMMIES.remove(data.uuid);
                        DummyWatchdog.ACTIVE_DUMMIES.put(fallbackUuid, new DummyWatchdog.DummyData(fallbackUuid, data.dimension, data.x, data.y, data.z, data.yRot, data.xRot, data.armor, data.hands));
                    }
                }
            }
        }
    }
}