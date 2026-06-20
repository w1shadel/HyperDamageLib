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
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                    entity -> entity != attacker && entity.isAlive() && !entity.isSpectator()
            );
            DamageSource erosionSource = DecayDamageUtil.getErosionSource(level, attacker);
            boolean hitAny = false;
            for (LivingEntity target : targets) {
                Vec3 toTarget = target.getEyePosition(1.0F).subtract(eyePosition);
                double distance = toTarget.length();
                if (distance <= range) {
                    double dot = lookVector.dot(toTarget.normalize());
                    if (dot > 0.5D) {
                        if (target instanceof IDecayEntity decayTarget) {
                            target.hurt(erosionSource, Integer.MAX_VALUE);
                            if (level instanceof ServerLevel serverLevel) {
                                serverLevel.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + (target.getBbHeight() / 2.0F), target.getZ(), 15, 0.3D, 0.3D, 0.3D, 0.1D);
                                serverLevel.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + (target.getBbHeight() / 2.0F), target.getZ(), 10, 0.2D, 0.2D, 0.2D, 0.1D);
                            }
                            level.playSound(null, target.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 1.3F);
                            level.playSound(null, target.blockPosition(), SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.5F, 1.5F);
                            if (attacker instanceof Player player) {
                                player.displayClientMessage(Component.literal(String.format(
                                        "§d[超貫通・空間薙ぎ払い] %s に " + Integer.MAX_VALUE + " の絶対ダメージを与えました。残HP: %.2f",
                                        target.getDisplayName().getString(), target.getHealth()
                                )), true);
                            }
                            hitAny = true;
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
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return super.hurtEnemy(stack, target, attacker);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            if (player instanceof IDecayEntity decayPlayer) {
                if (player.isShiftKeyDown()) {
                    boolean nextState = !decayPlayer.isSuperInvincible();
                    decayPlayer.setSuperInvincible(nextState);
                    if (nextState) {
                        player.displayClientMessage(Component.literal("§d[超無敵モード] §fON に設定しました。絶対死を回避します。"), true);
                        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.5F);
                        if (level instanceof ServerLevel serverLevel) {
                            serverLevel.sendParticles(ParticleTypes.WITCH, player.getX(), player.getY() + 1.0D, player.getZ(), 30, 0.5D, 0.5D, 0.5D, 0.1D);
                        }
                    } else {
                        player.displayClientMessage(Component.literal("§7[超無敵モード] §7OFF に設定しました。通常の状態に戻ります。"), true);
                        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 1.5F);
                    }
                } else {
                    double aoeRange = 6.0D;
                    AABB searchBox = player.getBoundingBox().inflate(aoeRange);
                    List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                            entity -> entity.isAlive() && !entity.isSpectator()
                    );
                    DamageSource erosionSource = DecayDamageUtil.getErosionSource(level, player);
                    DamageSource voidShredSource = DecayDamageUtil.getVoidShredSource(level, player);
                    boolean hitAny = false;
                    for (LivingEntity target : targets) {
                        target.hurt(voidShredSource, 10.0F);
                        if (level instanceof ServerLevel serverLevel) {
                            serverLevel.sendParticles(
                                    ParticleTypes.SOUL,
                                    target.getX(),
                                    target.getY() + (target.getBbHeight() / 2.0F),
                                    target.getZ(),
                                    10, 0.2D, 0.2D, 0.2D, 0.1D
                            );
                        }
                        hitAny = true;
                    }
                    if (hitAny) {
                        player.displayClientMessage(Component.literal("§c[絶対侵食波] 周囲の存在に侵食を付与し、絶対ダメージを与えました。"), true);
                    } else {
                        player.displayClientMessage(Component.literal("§7[絶対侵食波] 衝撃波を放ちましたが、周囲に敵がいませんでした。"), true);
                    }
                    player.getCooldowns().addCooldown(this, 00);
                }
            }
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7左クリック：前方の空間を薙ぎ払い、最大HP上限を永続的に削る。"));
        tooltip.add(Component.literal("§d攻撃ボーナス：範囲内の全員に防御無視の §f15.00 絶対ダメージ §dを直接付与。"));
        tooltip.add(Component.literal("§c右クリック：周囲6mの全員に絶対防御貫通の §f30.0 侵食衝撃波 §cを放つ。"));
        tooltip.add(Component.literal("§dShift + 右クリック：自身の「超無敵モード」を切り替える。"));
        tooltip.add(Component.literal("§d[Mixin 優先度上書きシステム搭載]"));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}