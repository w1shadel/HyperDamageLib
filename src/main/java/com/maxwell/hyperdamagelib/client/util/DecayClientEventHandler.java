package com.maxwell.hyperdamagelib.client.util;

import com.maxwell.hyperdamagelib.HDL;
import com.maxwell.hyperdamagelib.util.InvincibleHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HDL.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DecayClientEventHandler {
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        DecayClientEffectHelper.clientTick();

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null && InvincibleHelper.isInvincible(player)) {
            player.dead = false;
            player.deathTime = 0;
            if (player.getPose() == Pose.DYING) {
                player.setPose(Pose.STANDING);
            }
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