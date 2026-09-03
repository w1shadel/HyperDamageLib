package com.maxwell.hyperdamagelib.client.util;

import com.maxwell.hyperdamagelib.HDL;
import com.maxwell.hyperdamagelib.util.InvincibleHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HDL.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DecayClientEventHandler {
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            DecayClientEffectHelper.clientTick();
            maintainClientInvincibleState();
        }
    }

    private static void maintainClientInvincibleState() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (InvincibleHelper.isInvincible(player)) {
            InvincibleHelper.keepAlive(player);
        }
    }

    @SubscribeEvent
    public static void onClientRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof LocalPlayer localPlayer) {
            InvincibleHelper.keepAlive(localPlayer);
        }
    }
}