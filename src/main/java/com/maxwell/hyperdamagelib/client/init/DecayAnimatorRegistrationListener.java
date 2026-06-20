package com.maxwell.hyperdamagelib.client.init;

import com.maxwell.hyperdamagelib.HDL;
import com.maxwell.hyperdamagelib.client.event.RegisterDecayAnimatorsEvent;
import com.maxwell.hyperdamagelib.init.ModItems;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HDL.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DecayAnimatorRegistrationListener {

    @SubscribeEvent
    public static void onRegisterAnimators(RegisterDecayAnimatorsEvent event) {
        System.out.println("[HDL-DEBUG] RegisterDecayAnimatorsEvent received inside listener!");

        try {
            event.register(ModItems.EROSION_SWORD.get(), (guiGraphics, slotIndex, stack, time) -> {
                // スピン
                float spinDirection = (float) Math.sin(time * 0.0007D);
                float angle = (time * 0.06F) * spinDirection + (slotIndex * 15.0F);
                guiGraphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angle));

                // 浮遊
                float floatOffset = (float) Math.sin((time * 0.0022D) + slotIndex) * 2.2F;
                guiGraphics.pose().translate(0.0F, floatOffset, 0.0F);

                // 引き延ばし
                float pulse = (float) Math.max(0.0, Math.sin(time * 0.001D));
                float spike = (float) Math.pow(pulse, 16);
                float scaleX = 1.0F + (spike * 0.6F);
                float scaleY = 1.0F - (spike * 0.5F);
                guiGraphics.pose().scale(scaleX, scaleY, 1.0F);
            });
            System.out.println("[HDL-DEBUG] EROSION_SWORD successfully registered to DecayItemAnimationRegistry!");
        } catch (Exception e) {
            System.err.println("[HDL-DEBUG] Failed to register EROSION_SWORD animator: " + e.getMessage());
            e.printStackTrace();
        }
    }
}