package com.maxwell.hyperdamagelib.client.util;

import com.maxwell.hyperdamagelib.HDL;
import com.maxwell.hyperdamagelib.util.IDecayEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HDL.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DecayClientEventHandler {
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            DecayClientEffectHelper.clientTick();
        }
    }

    @SubscribeEvent
    public static void onClientTickSafetyRenderSalvage(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof LivingEntity living && entity instanceof IDecayEntity decay) {
                if (decay.isSuperInvincible() && (living.deathTime > 0 || living.dead)) {
                    living.dead = false;
                    living.deathTime = 0;
                    if (living.getPose() == net.minecraft.world.entity.Pose.DYING) {
                        living.setPose(net.minecraft.world.entity.Pose.STANDING);
                    }
                }
            }
        }
    }
}