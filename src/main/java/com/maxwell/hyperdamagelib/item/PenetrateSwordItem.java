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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class PenetrateSwordItem extends SwordItem {
    public PenetrateSwordItem(Properties properties) {
        super(Tiers.NETHERITE, 3, -2.4F, properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!attacker.level().isClientSide()) {
            DamageSource source = DecayDamageUtil.getPenetrateSource(attacker.level(), attacker);
            DecayDamageUtil.applyCustomDamage(target, source, 18.0F);
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            AABB aoe = player.getBoundingBox().inflate(5.0D);
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, aoe, e -> e != player && e.isAlive());
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