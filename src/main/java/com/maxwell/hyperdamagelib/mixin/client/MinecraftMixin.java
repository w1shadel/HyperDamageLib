package com.maxwell.hyperdamagelib.mixin.client;

import com.maxwell.hyperdamagelib.util.IDecayEntity;
import com.maxwell.hyperdamagelib.util.InvincibleHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    public void setScreenMixin(Screen screen, CallbackInfo ci) {
        Minecraft mc = (Minecraft) (Object) this;
        LocalPlayer player = mc.player;
        if (player == null) return;
        if (screen instanceof DeathScreen) {
            if (InvincibleHelper.isInvincible(player) ||
                    (player instanceof IDecayEntity decay && decay.isSuperInvincible())) {
                ci.cancel();
            }
        }
    }
}
