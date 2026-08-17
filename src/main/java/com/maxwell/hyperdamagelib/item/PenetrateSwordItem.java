package com.maxwell.hyperdamagelib.item;

import com.maxwell.hyperdamagelib.util.DecayDamageUtil;
import net.minecraft.core.particles.ParticleTypes;
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

public class PenetrateSwordItem extends SwordItem {
    public PenetrateSwordItem(Properties properties) {
        super(Tiers.NETHERITE, 3, -2.4F, properties);
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity target) {
        if (!player.level().isClientSide() && target instanceof LivingEntity livingTarget) {
            DamageSource source = DecayDamageUtil.getPenetrateSource(player.level(), player);
            DecayDamageUtil.applyCustomDamage(livingTarget, source, 18.0F);
        }
        return true;
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity attacker) {
        Level level = attacker.level();
        if (!level.isClientSide()) {
            double range = 4.5D;
            Vec3 eyePos = attacker.getEyePosition(1.0F);
            Vec3 lookVec = attacker.getLookAngle();
            AABB searchBox = attacker.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.5D);
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                    e -> e != attacker && !e.isSpectator());
            DamageSource source = DecayDamageUtil.getPenetrateSource(level, attacker);
            boolean hit = false;
            for (LivingEntity target : targets) {
                Vec3 toTarget = target.getEyePosition(1.0F).subtract(eyePos);
                if (toTarget.length() <= range && lookVec.dot(toTarget.normalize()) > 0.4D) {
                    DecayDamageUtil.applyCustomDamage(target, source, 18.0F);
                    if (level instanceof ServerLevel serverLevel) {
                        serverLevel.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 1.0, target.getZ(), 8, 0.1, 0.1, 0.1, 0.1);
                    }
                    hit = true;
                }
            }
            if (hit) {
                level.playSound(null, attacker.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8F, 1.2F);
            }
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            AABB aoe = player.getBoundingBox().inflate(5.0D);
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, aoe, e -> e != player);
            DamageSource source = DecayDamageUtil.getPenetrateSource(level, player);
            for (LivingEntity target : targets) {
                DecayDamageUtil.applyCustomDamage(target, source, 15.0F);
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.FLASH, target.getX(), target.getY() + 1.0, target.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
                }
            }
            level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.8F, 1.4F);
            player.getCooldowns().addCooldown(this, 40);
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}