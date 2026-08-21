package com.maxwell.hyperdamagelib.item;

import com.maxwell.hyperdamagelib.util.DecayDamageUtil;
import com.maxwell.hyperdamagelib.util.IDecayEntity;
import com.maxwell.hyperdamagelib.util.InvincibleHelper;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class ErosionSwordItem extends SwordItem {
    public ErosionSwordItem(Properties properties) {
        super(Tiers.NETHERITE, 4, -2.4F, properties);
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity target) {
        if (!player.level().isClientSide() && target instanceof LivingEntity livingTarget) {
            DamageSource source = DecayDamageUtil.getErosionSource(player.level(), player);
            DecayDamageUtil.applyCustomDamage(livingTarget, source, 30.0F);
        }
        return true;
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity attacker) {
        Level level = attacker.level();
        if (!level.isClientSide()) {
            double range = 5.0D;
            Vec3 eyePos = attacker.getEyePosition(1.0F);
            Vec3 lookVec = attacker.getLookAngle();
            AABB searchBox = attacker.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(2.0D);
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                    e -> e != attacker && !e.isSpectator());
            DamageSource source = DecayDamageUtil.getErosionSource(level, attacker);
            boolean hit = false;
            for (LivingEntity target : targets) {
                Vec3 toTarget = target.getEyePosition(1.0F).subtract(eyePos);
                if (toTarget.length() <= range && lookVec.dot(toTarget.normalize()) > 0.35D) {
                    DecayDamageUtil.applyCustomDamage(target, source, 30.0F);
                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 1.0, target.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
                    }
                    hit = true;
                }
            }
            if (hit) {
                level.playSound(null, attacker.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.0F, 1.2F);
            }
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            if (player.isShiftKeyDown()) {
                boolean nextState = !InvincibleHelper.isInvincible(player);
                if (player instanceof IDecayEntity decay) {
                    decay.setSuperInvincible(nextState);
                    if (nextState) {
                        float lockedHp = decay.getInvincibleHealthValue();
                        player.displayClientMessage(
                                Component.translatable("message.hyperdamagelib.super_invincible.on_current", String.format("%.1f", lockedHp)),
                                true
                        );
                    } else {
                        try {
                            DecayDamageUtil.BYPASS_DECAY.set(true);
                            player.setHealth(decay.getInvincibleHealthValue());
                            decay.setDecayAmount(0.0F);
                            decay.setDecayHoldTicks(0);
                            player.dead = false;
                            player.deathTime = 0;
                        } finally {
                            DecayDamageUtil.BYPASS_DECAY.remove();
                        }
                        player.displayClientMessage(
                                Component.translatable("message.hyperdamagelib.super_invincible.off"),
                                true
                        );
                    }
                }
                level.playSound(null, player.blockPosition(), nextState ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 1.5F);
            }
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}