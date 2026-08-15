package com.maxwell.hyperdamagelib.mixin;

import com.maxwell.hyperdamagelib.util.InvincibleHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ForgeHooks.class, remap = false)
public class ForgeHooksMixin {
    @Inject(method = "onLivingDeath", at = @At("HEAD"), cancellable = true)
    private static void onLivingDeathMixin(LivingEntity entity, DamageSource src, CallbackInfoReturnable<Boolean> cir) {
        if (InvincibleHelper.isInvincible(entity)) {
            // true を返すと Forge は「イベントがキャンセルされた」と判定し、
            // バニラ ServerPlayer.die() の先頭にある if (onLivingDeath) return; で即座に終了する
            cir.setReturnValue(true);
            cir.cancel();
        }
    }

    @Inject(method = "onLivingAttack", at = @At("HEAD"), cancellable = true)
    private static void onLivingAttackMixin(LivingEntity entity, DamageSource src, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (InvincibleHelper.isInvincible(entity)) {
            // false を返すとバニラ LivingEntity.hurt() は1行目で何もせず return false する
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    @Inject(method = "onLivingHurt", at = @At("HEAD"), cancellable = true)
    private static void onLivingHurtMixin(LivingEntity entity, DamageSource src, float amount, CallbackInfoReturnable<Float> cir) {
        if (InvincibleHelper.isInvincible(entity)) {
            cir.setReturnValue(0.0F);
            cir.cancel();
        }
    }
}