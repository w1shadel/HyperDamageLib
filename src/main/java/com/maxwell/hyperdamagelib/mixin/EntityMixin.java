package com.maxwell.hyperdamagelib.mixin;

import com.maxwell.hyperdamagelib.util.InvincibleHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class EntityMixin {

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
        }
    }

    @Inject(method = "kill", at = @At("HEAD"), cancellable = true)
    private void decay$preventKill(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (InvincibleHelper.isInvincible(entity) || (InvincibleHelper.isDummy(entity) && !InvincibleHelper.isRemoveBypass(entity))) {
            ci.cancel();
        }
    }

    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true)
    private void decay$preventRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (InvincibleHelper.isRemoveBypass(entity)) {
            return;
        }
        if (InvincibleHelper.isInvincible(entity) || InvincibleHelper.isDummy(entity)) {
            ci.cancel();
        }
    }
}