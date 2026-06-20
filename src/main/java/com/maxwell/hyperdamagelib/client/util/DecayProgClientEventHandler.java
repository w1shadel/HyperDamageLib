package com.maxwell.hyperdamagelib.client.util;

import com.maxwell.hyperdamagelib.HDL;
import com.maxwell.hyperdamagelib.mixin.client.PostChainAccessor;
import com.maxwell.hyperdamagelib.mixin.client.PostPassAccessor;
import com.maxwell.hyperdamagelib.util.IDecayEntity;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HDL.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DecayProgClientEventHandler {

    private static final ResourceLocation DECAY_SHADER = new ResourceLocation(HDL.MODID, "shaders/post/decay.json");

    private static float renderIntensity = 0.0F;

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        float targetIntensity = 0.0F;
        if (player != null && !player.isSpectator() && player instanceof IDecayEntity decay) {
            float decayAmount = decay.getDecayAmount();
            float maxHealth = player.getMaxHealth();
            if (decayAmount > 0.0F && maxHealth > 0.0F) {
                targetIntensity = decayAmount / maxHealth;
            }
        }




        float lerpSpeed = 0.04F;
        renderIntensity += (targetIntensity - renderIntensity) * lerpSpeed;

        if (Math.abs(renderIntensity - targetIntensity) < 0.001F) {
            renderIntensity = targetIntensity;
        }

        if (renderIntensity > 0.0F) {
            GameRenderer renderer = mc.gameRenderer;

            if (renderer.currentEffect() == null || !DECAY_SHADER.toString().equals(renderer.currentEffect().getName())) {
                renderer.loadEffect(DECAY_SHADER);
            }

            PostChain chain = renderer.currentEffect();
            if (chain instanceof PostChainAccessor accessor) {
                for (PostPass pass : accessor.getPasses()) {
                    if (pass instanceof PostPassAccessor passAccessor) {
                        EffectInstance effect = passAccessor.getEffect();

                        Uniform intensityUniform = effect.getUniform("Intensity");
                        if (intensityUniform != null) {
                            intensityUniform.set(renderIntensity);
                        }

                        Uniform timeUniform = effect.getUniform("DecayTime");
                        if (timeUniform != null) {
                            timeUniform.set((System.currentTimeMillis() % 100000L) / 1000.0F);
                        }
                    }
                }
            }
        } else {

            shutdownShaderSafely(mc.gameRenderer);
        }
    }

    private static void shutdownShaderSafely(GameRenderer renderer) {
        if (renderer.currentEffect() != null && DECAY_SHADER.toString().equals(renderer.currentEffect().getName())) {
            renderer.shutdownEffect();
        }
    }
}