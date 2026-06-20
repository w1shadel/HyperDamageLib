package com.maxwell.hyperdamagelib.client.util;

import com.maxwell.hyperdamagelib.HDL;
import com.maxwell.hyperdamagelib.init.ModItems;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

public class DecayClientSetup {

    public static void onClientSetup(FMLClientSetupEvent event) {
        HDL.LOGGER.info("[HDL-DEBUG] Client setup initializing...");

        event.enqueueWork(() -> {
            try {


                DecayItemAnimationRegistry.register(ModItems.EROSION_SWORD.get(),
                        DecayAnimationConfig.builder()
                                .animator((poseStack, displayContext, stack, time) -> {
                                    double elapsedSeconds = (time % 3600000L) / 1000.0;
                                    float intensity = 1.0F;

                                    if (displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
                                        intensity = 0.22F;
                                    } else if (displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
                                        intensity = 0.60F;
                                    } else if (displayContext == ItemDisplayContext.GROUND || displayContext == ItemDisplayContext.FIXED) {
                                        intensity = 0.40F;
                                    } else if (displayContext == ItemDisplayContext.GUI) {
                                        intensity = 1.0F;
                                    }

                                    float swayAngle = (float) (Math.sin(elapsedSeconds * 3.5) * 12.0 + Math.cos(elapsedSeconds * 7.7) * 6.0);
                                    float jitterAngle = (float) (Math.sin(elapsedSeconds * 28.0) * 6.0 + Math.cos(elapsedSeconds * 53.0) * 3.0);
                                    float angle = (swayAngle + jitterAngle) * intensity;

                                    float jitterX = (float) (Math.sin(elapsedSeconds * 40.0) * 0.015 + Math.cos(elapsedSeconds * 75.0) * 0.006) * intensity;
                                    float jitterY = (float) (Math.cos(elapsedSeconds * 35.0) * 0.015 + Math.sin(elapsedSeconds * 80.0) * 0.006) * intensity;
                                    float jitterZ = (float) (Math.sin(elapsedSeconds * 45.0) * 0.01) * intensity;

                                    double spikePhase = (elapsedSeconds * 1.5) % (Math.PI * 2);
                                    float rawPulse = (float) Math.max(0.0, Math.sin(spikePhase));
                                    float spike = (float) Math.pow(rawPulse, 24);

                                    float scaleX = 1.0F + (spike * 1.5F * intensity);
                                    float scaleY = 1.0F - (spike * 0.5F * intensity);
                                    float scaleZ = 1.0F + (spike * 0.4F * intensity);

                                    poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angle));
                                    poseStack.translate(jitterX, jitterY, jitterZ);
                                    poseStack.scale(scaleX, scaleY, scaleZ);
                                })
                                .aura(true) 
                                .auraColor(140, 15, 195, 115) 
                                .auraScale(0.44F) 
                                .auraRotateSpeed(-40.0F) 
                                .auraPulse(4.0F, 0.12F) 
                                .auraShape(DecayAnimationConfig.AuraShape.OCTAGON) 
                                .build()
                );
            } catch (Exception e) {
                HDL.LOGGER.error("[HDL-DEBUG] Failed to register EROSION_SWORD animator", e);
            }
        });
    }
}