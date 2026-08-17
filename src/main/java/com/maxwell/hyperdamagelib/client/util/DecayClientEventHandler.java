package com.maxwell.hyperdamagelib.client.util;

import com.maxwell.hyperdamagelib.HDL;
import com.maxwell.hyperdamagelib.util.IDecayEntity;
import com.maxwell.hyperdamagelib.util.InvincibleHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
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
            player.dead = false;
            player.deathTime = 0;
            if (player.getPose() == Pose.DYING) {
                player.setPose(Pose.STANDING);
            }
        }
    }

    @SubscribeEvent
    public static void onClientRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof LocalPlayer localPlayer) {
            localPlayer.dead = false;
            localPlayer.deathTime = 0;
            localPlayer.setPose(Pose.STANDING);
            if (localPlayer instanceof IDecayEntity decay) {
                decay.setDecayAmount(0.0F);
            }
        }
    }

    @SubscribeEvent
    public static void onClientTickSafetyRenderSalvage(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && entity instanceof IDecayEntity decay) {
                if (decay.isSuperInvincible() && (living.deathTime > 0 || living.dead)) {
                    living.dead = false;
                    living.deathTime = 0;
                    if (living.getPose() == Pose.DYING) {
                        living.setPose(Pose.STANDING);
                    }
                }
            }
        }
    }
}