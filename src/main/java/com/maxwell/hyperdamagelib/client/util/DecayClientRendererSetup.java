package com.maxwell.hyperdamagelib.client.util;

import com.maxwell.hyperdamagelib.HDL;
import com.maxwell.hyperdamagelib.client.renderer.MeasurementDummyRenderer;
import com.maxwell.hyperdamagelib.init.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HDL.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class DecayClientRendererSetup {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MEASUREMENT_DUMMY.get(), MeasurementDummyRenderer::new);
    }
}