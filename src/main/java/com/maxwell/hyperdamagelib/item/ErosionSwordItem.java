package com.maxwell.hyperdamagelib.item;

import com.maxwell.hyperdamagelib.util.DecayDamageUtil;
import com.maxwell.hyperdamagelib.util.IDecayEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ErosionSwordItem extends SwordItem {
    public ErosionSwordItem(Properties properties) {
        super(Tiers.NETHERITE, 3, -2.4F, properties);
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity attacker) {
        Level level = attacker.level();
        if (!level.isClientSide()) {
            double range = 4.5D;
            double width = 1.8D;
            Vec3 eyePosition = attacker.getEyePosition(1.0F);
            Vec3 lookVector = attacker.getLookAngle();
            AABB searchBox = attacker.getBoundingBox()
                    .expandTowards(lookVector.scale(range))
                    .inflate(width, 1.0D, width);

            List<Entity> rawTargets = level.getEntities(attacker, searchBox,
                    entity -> entity != attacker && entity.isAlive() && !entity.isSpectator()
            );

            DamageSource erosionSource = DecayDamageUtil.getErosionSource(level, attacker);
            boolean hitAny = false;

            for (Entity rawTarget : rawTargets) {
                Entity actualTarget = rawTarget;

                if (rawTarget instanceof net.minecraftforge.entity.PartEntity<?> part) {
                    Entity parent = part.getParent();
                    if (parent != null) {
                        actualTarget = parent;
                    }
                }

                if (actualTarget instanceof LivingEntity target) {
                    Vec3 toTarget = target.getEyePosition(1.0F).subtract(eyePosition);
                    double distance = toTarget.length();

                    if (distance <= range) {
                        double dot = lookVector.dot(toTarget.normalize());
                        if (dot > 0.5D) {

                            float maxHp = target.getMaxHealth();
                            if (maxHp >= 3.0E38F || Float.isInfinite(maxHp) || Float.isNaN(maxHp)) {
                                com.maxwell.hyperdamagelib.util.DecayForceKillHelper.decayForceKill(target);
                                if (level instanceof ServerLevel serverLevel) {
                                    serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + (target.getBbHeight() / 2.0F), target.getZ(), 30, 0.4D, 0.4D, 0.4D, 0.15D);
                                }
                                hitAny = true;
                                continue;
                            }

                            if (target instanceof IDecayEntity decayTarget) {

                                boolean success = target.hurt(erosionSource, 20.0F); 


                                if (!success) {

                                    float decayAmount = target.getMaxHealth() * 0.20F;
                                    decayTarget.addDecayAmount(decayAmount);

                                    float currentHealth = target.getHealth();
                                    float targetHealth = Math.max(0.0F, currentHealth - 20.0F);
                                    target.setHealth(targetHealth);
                                } else {

                                    target.hurt(erosionSource, Integer.MAX_VALUE);
                                }

                                if (level instanceof ServerLevel serverLevel) {
                                    serverLevel.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + (target.getBbHeight() / 2.0F), target.getZ(), 15, 0.3D, 0.3D, 0.3D, 0.1D);
                                    serverLevel.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + (target.getBbHeight() / 2.0F), target.getZ(), 10, 0.2D, 0.2D, 0.2D, 0.1D);
                                }
                                level.playSound(null, target.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 1.3F);
                                level.playSound(null, target.blockPosition(), SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.5F, 1.5F);

                                if (attacker instanceof Player player) {
                                    player.displayClientMessage(Component.translatable(
                                            "message.hyperdamagelib.erosion.swipe_hit",
                                            target.getDisplayName(),
                                            "Absolute",
                                            target.getHealth()
                                    ), true);
                                }
                                hitAny = true;
                            }
                        }
                    }
                }
            }
            if (!hitAny) {
                level.playSound(null, attacker.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.2F);
            }
        }
        return super.onEntitySwing(stack, attacker);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            if (player instanceof IDecayEntity decayPlayer) {
                if (player.isShiftKeyDown()) {

                    if (!decayPlayer.isSuperInvincible()) {
                        decayPlayer.setKeepCurrentHealth(true);
                        decayPlayer.setSuperInvincible(true);
                        player.displayClientMessage(Component.translatable("message.hyperdamagelib.super_invincible.on_current", player.getHealth()), true);

                        level.playSound(null, player.blockPosition(), SoundEvents.CONDUIT_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.7F);
                    }

                    else if (decayPlayer.isKeepCurrentHealth()) {
                        decayPlayer.setKeepCurrentHealth(false);
                        decayPlayer.setSuperInvincible(true); 
                        player.displayClientMessage(Component.translatable("message.hyperdamagelib.super_invincible.on_max"), true);

                        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.5F);
                    }

                    else {
                        decayPlayer.setSuperInvincible(false);
                        decayPlayer.setKeepCurrentHealth(false);
                        player.displayClientMessage(Component.translatable("message.hyperdamagelib.super_invincible.off"), true);

                        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 1.5F);
                    }

                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0D, player.getZ(), 30, 0.5D, 0.5D, 0.5D, 0.1D);
                    }
                } else {
                    double aoeRange = 6.0D;
                    AABB searchBox = player.getBoundingBox().inflate(aoeRange);
                    List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                            entity -> entity != player && entity.isAlive() && !entity.isSpectator()
                    );
                    DamageSource erosionSource = DecayDamageUtil.getErosionSource(level, player);
                    boolean hitAny = false;
                    for (LivingEntity target : targets) {
                        target.hurt(erosionSource, 10.0F);
                        if (level instanceof ServerLevel serverLevel) {
                            serverLevel.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + (target.getBbHeight() / 2.0F), target.getZ(), 10, 0.2D, 0.2D, 0.2D, 0.1D);
                        }
                        hitAny = true;
                    }

                    if (hitAny) {
                        player.displayClientMessage(Component.translatable("message.hyperdamagelib.erosion.wave_hit"), true);
                    } else {
                        player.displayClientMessage(Component.translatable("message.hyperdamagelib.erosion.wave_miss"), true);
                    }
                    player.getCooldowns().addCooldown(this, 60);
                }
            }
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.hyperdamagelib.erosion_sword.tooltip_1"));
        tooltip.add(Component.translatable("item.hyperdamagelib.erosion_sword.tooltip_2"));
        tooltip.add(Component.translatable("item.hyperdamagelib.erosion_sword.tooltip_3"));
        tooltip.add(Component.translatable("item.hyperdamagelib.erosion_sword.tooltip_4"));
        tooltip.add(Component.translatable("item.hyperdamagelib.erosion_sword.tooltip_5"));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}