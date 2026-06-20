package com.maxwell.hyperdamagelib.mixin.client;

import com.maxwell.hyperdamagelib.util.IDecayEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Shadow
    @Nullable
    public LocalPlayer player;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    public void setScreenMixin(Screen screen, CallbackInfo ci) {
        if (player == null) return;
        if (screen instanceof DeathScreen && player instanceof IDecayEntity decay) {
            if (decay.isSuperInvincible() && !player.isDeadOrDying()) {
                ci.cancel();
            }
        }
    }
}