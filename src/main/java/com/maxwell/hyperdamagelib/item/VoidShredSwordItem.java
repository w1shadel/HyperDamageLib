package com.maxwell.hyperdamagelib.item;

import com.maxwell.hyperdamagelib.util.DecayDamageUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
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

public class VoidShredSwordItem extends SwordItem {
    public VoidShredSwordItem(Properties properties) {
        super(Tiers.NETHERITE, 4, -2.2F, properties);
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity attacker) {
        Level level = attacker.level();
        if (!level.isClientSide()) {
            double range = 5.0D;
            double width = 2.0D;
            Vec3 eyePosition = attacker.getEyePosition(1.0F);
            Vec3 lookVector = attacker.getLookAngle();
            AABB searchBox = attacker.getBoundingBox()
                    .expandTowards(lookVector.scale(range))
                    .inflate(width, 1.0D, width);

            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                    entity -> entity != attacker && entity.isAlive() && !entity.isSpectator()
            );

            DamageSource voidShredSource = DecayDamageUtil.getVoidShredSource(level, attacker);
            boolean hitAny = false;

            for (LivingEntity target : targets) {
                Vec3 toTarget = target.getEyePosition(1.0F).subtract(eyePosition);
                double distance = toTarget.length();
                if (distance <= range) {
                    double dot = lookVector.dot(toTarget.normalize());

                    if (dot > 0.5D) {

                        target.hurt(voidShredSource, 25.0F);

                        if (level instanceof ServerLevel serverLevel) {

                            serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + (target.getBbHeight() / 2.0F), target.getZ(), 20, 0.4D, 0.4D, 0.4D, 0.15D);
                            serverLevel.sendParticles(ParticleTypes.PORTAL, target.getX(), target.getY() + (target.getBbHeight() / 2.0F), target.getZ(), 10, 0.2D, 0.2D, 0.2D, 0.2D);
                        }
                        level.playSound(null, target.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.8F);
                        level.playSound(null, target.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.4F, 1.6F);
                        hitAny = true;
                    }
                }
            }

            if (hitAny) {
                level.playSound(null, attacker.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 0.7F);
            } else {
                level.playSound(null, attacker.blockPosition(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.5F, 1.5F);
            }
        }
        return super.onEntitySwing(stack, attacker);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            double pullRange = 8.0D;
            Vec3 playerPos = player.position();
            AABB pullBox = player.getBoundingBox().inflate(pullRange);
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, pullBox,
                    entity -> entity != player && entity.isAlive() && !entity.isSpectator()
            );

            DamageSource voidShredSource = DecayDamageUtil.getVoidShredSource(level, player);
            boolean affected = false;

            for (LivingEntity target : targets) {

                Vec3 pullDir = playerPos.subtract(target.position()).normalize();
                double distance = target.position().distanceTo(playerPos);

                double pullForce = Math.max(0.2D, (pullRange - distance) * 0.25D);
                target.setDeltaMovement(target.getDeltaMovement().add(pullDir.scale(pullForce)));
                target.hurtMarked = true; 

                target.hurt(voidShredSource, 15.0F);

                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.PORTAL, target.getX(), target.getY() + (target.getBbHeight() / 2.0F), target.getZ(), 15, 0.1D, 0.1D, 0.1D, 0.2D);
                }
                affected = true;
            }

            if (affected) {
                player.displayClientMessage(Component.literal("§d[空間重力崩壊] 周囲の敵を吸引し、空間を引き裂きました。"), true);
                level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.7F, 1.4F);
                level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5F, 1.8F);

                if (level instanceof ServerLevel serverLevel) {

                    serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0D, player.getZ(), 60, 0.8D, 0.8D, 0.8D, 0.3D);
                }
            } else {
                player.displayClientMessage(Component.literal("§7[空間重力崩壊] 空間が歪みましたが、引き寄せる敵がいませんでした。"), true);
                level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 0.4F, 2.0F);
            }

            player.getCooldowns().addCooldown(this, 60);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§d[Void Shred 空間裂断刃]"));
        tooltip.add(Component.literal("§7左クリック：前方の空間を切り裂き、広範囲の敵に §f25.0 空間ダメージ §7を与える。"));
        tooltip.add(Component.literal("§c右クリック：周囲8mの敵を自身へ「強制吸引」し、§f15.0 空間崩壊ダメージ §cを与える。"));
        tooltip.add(Component.literal("§d[空間を切り裂く特異点エネルギー搭載]"));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}