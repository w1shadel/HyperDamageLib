package com.maxwell.hyperdamagelib.mixin;

import com.maxwell.hyperdamagelib.util.IDecayEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ServerPlayer.class, priority = -10000000)
public abstract class ServerPlayerMixin {
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void decay$preventServerPlayerDie(DamageSource source, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (player.connection == null) {
            ci.cancel();
            return;
        }
        if ((Object) this instanceof IDecayEntity decay && decay.isSuperInvincible()) {

            ci.cancel();
        }
    }
}