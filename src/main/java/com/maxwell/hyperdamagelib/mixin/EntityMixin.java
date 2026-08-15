package com.maxwell.hyperdamagelib.mixin;

import com.maxwell.hyperdamagelib.entity.MeasurementDummyEntity;
import com.maxwell.hyperdamagelib.util.IDecayEntity;
import com.maxwell.hyperdamagelib.util.InvincibleHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

    // 攻撃のクリック・Raycastターゲットから完全に消滅する
    @Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
    private void decay$isPickable(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
    @Inject(method = "saveWithoutId", at = @At("HEAD"))
    public void saveWithoutIdMixin(CompoundTag pCompound, CallbackInfoReturnable<CompoundTag> cir) {
        Entity entity = (Entity) (Object) this;
        if (InvincibleHelper.isInvincible(entity)) {
            pCompound.putBoolean("hyperdamagelib:super_invincible", true);
        }
    }

    @Inject(method = "load", at = @At("RETURN"))
    public void loadMixin(CompoundTag compoundTag, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (compoundTag.getBoolean("hyperdamagelib:super_invincible")) {
            InvincibleHelper.setInvincible(entity, true);
            if (entity instanceof IDecayEntity decay) {
                decay.setSuperInvincible(true);
            }
        }
    }
    // 攻撃可能判定を無効化
    @Inject(method = "isAttackable", at = @At("HEAD"), cancellable = true)
    private void decay$isAttackable(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    // 飛び道具（SugarBow等）の当たり判定をすり抜けさせる
    @Inject(method = "canBeHitByProjectile", at = @At("HEAD"), cancellable = true)
    private void decay$canBeHitByProjectile(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    @Inject(method = "kill", at = @At("HEAD"), cancellable = true)
    private void decay$preventKill(CallbackInfo ci) {
        if ((Object) this instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            ci.cancel();
        }
        if ((Object) this instanceof MeasurementDummyEntity dummy && !dummy.isRemoveBypass()) {
            ci.cancel();
        }
    }

    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true)
    private void decay$preventRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
        if ((Object) this instanceof IDecayEntity decay && decay.isSuperInvincible()) {
            if (!decay.isRemoveBypass()) {
                ci.cancel();
            }
        }
        if ((Object) this instanceof MeasurementDummyEntity dummy && !dummy.isRemoveBypass()) {
            ci.cancel();
        }
    }
}