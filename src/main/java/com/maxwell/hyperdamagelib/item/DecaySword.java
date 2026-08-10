package com.maxwell.hyperdamagelib.item;

import com.maxwell.hyperdamagelib.util.DecayDamageUtil;
import com.maxwell.hyperdamagelib.util.DecayForceKillHelper;
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

public class DecaySword extends SwordItem {
    public DecaySword(Properties properties) {
        super(Tiers.NETHERITE, -999999999, -2.4F, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            double aoeRange = 4.5D;
            AABB searchBox = player.getBoundingBox().inflate(aoeRange);
            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, searchBox,
                    entity -> entity != player && entity.isAlive() && !entity.isSpectator()
            );
            String customMessage = "%victim%'s stance was completely shattered by %attacker%'s Force Burst!";
            DamageSource penetrateSource = DecayDamageUtil.getPenetrateSource(level, player, customMessage);
            boolean hitAny = false;
            for (LivingEntity target : targets) {
                DecayForceKillHelper.decayForceKill(target);
                if (level instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.FLASH, target.getX(), target.getY() + (target.getBbHeight() / 2.0F), target.getZ(), 2, 0.1D, 0.1D, 0.1D, 0.0D);
                }
                hitAny = true;
            }
            if (hitAny) {
                player.displayClientMessage(Component.translatable("message.hyperdamagelib.decaysword.wave_hit"), true);
                level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.8F, 1.3F);
            } else {
                player.displayClientMessage(Component.translatable("message.hyperdamagelib.decaysword.wave_miss"), true);
                level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.5F, 1.2F);
            }
            player.getCooldowns().addCooldown(this, 50);
        }
        return InteractionResultHolder.success(stack);
    }
}
