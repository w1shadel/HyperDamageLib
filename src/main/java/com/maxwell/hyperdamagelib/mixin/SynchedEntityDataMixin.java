

package com.maxwell.hyperdamagelib.mixin;

import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.maxwell.hyperdamagelib.util.IDecayEntity;
import com.maxwell.hyperdamagelib.util.DecayDamageUtil;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SynchedEntityData.class)
public abstract class SynchedEntityDataMixin {
    @Shadow private boolean isDirty;

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private <T> void decay$lockEntityData(EntityDataAccessor<T> key, T value, CallbackInfo ci) {
        if (DecayDamageUtil.BYPASS_DECAY.get()) return;
    }
}