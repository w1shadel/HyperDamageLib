package com.maxwell.hyperdamagelib.mixin;

import com.maxwell.hyperdamagelib.util.InvincibleHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ForgeHooks.class, remap = false)
public class ForgeHooksMixin {

    @Inject(method = "onPlayerAttackTarget", at = @At("HEAD"), cancellable = true)
    private static void decay$forceNormalHurtForInvincible(Player player, Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (InvincibleHelper.isInvincible(target)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    @Inject(method = "onLivingDeath", at = @At("HEAD"), cancellable = true)
    private static void onLivingDeathMixin(LivingEntity entity, DamageSource src, CallbackInfoReturnable<Boolean> cir) {
        if (InvincibleHelper.isInvincible(entity)) {
            cir.setReturnValue(true);
            cir.cancel();
        }
    }
}