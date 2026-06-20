package com.maxwell.hyperdamagelib.client.util;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class DecayClientAnimationHelper {

    public static final ThreadLocal<ItemStack> CURRENT_RENDER_STACK = ThreadLocal.withInitial(() -> null);
    public static final ThreadLocal<ItemDisplayContext> CURRENT_RENDER_CONTEXT = ThreadLocal.withInitial(() -> null);
    public static final ThreadLocal<MultiBufferSource> CURRENT_RENDER_BUFFER = ThreadLocal.withInitial(() -> null);

    
    public static void renderGuiSlotAura(PoseStack poseStack, MultiBufferSource bufferSource, long time, DecayAnimationConfig config) {
        if (bufferSource == null || config == null || !config.hasAura()) return;

        poseStack.pushPose();

        poseStack.translate(0.0F, 0.0F, -0.05F);

        double elapsedSeconds = (time % 3600000L) / 1000.0;

        float pSpeed = config.getAuraPulseSpeed();
        float pAmp = config.getAuraPulseAmplitude();
        float pulseSize = (float) (Math.sin(elapsedSeconds * pSpeed) * pAmp + (1.0F - pAmp));

        float rSpeed = config.getAuraRotateSpeed();
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float)((elapsedSeconds * rSpeed) % 360.0)));
        poseStack.scale(pulseSize, pulseSize, 1.0F);

        int r = config.getRed();
        int g = config.getGreen();
        int b = config.getBlue();
        int a = config.getAlpha();
        float size = config.getAuraScale();

        net.minecraft.client.renderer.RenderType renderType = net.minecraft.client.renderer.RenderType.gui();
        com.mojang.blaze3d.vertex.VertexConsumer builder = bufferSource.getBuffer(renderType);

        DecayAnimationConfig.AuraShape shape = config.getAuraShape();

        if (shape == DecayAnimationConfig.AuraShape.SQUARE) {

            builder.vertex(poseStack.last().pose(), -size, -size, 0.0F).color(r, g, b, a).uv(0.0F, 0.0F).uv2(15728880).endVertex();
            builder.vertex(poseStack.last().pose(), size, -size, 0.0F).color(r, g, b, a).uv(1.0F, 0.0F).uv2(15728880).endVertex();
            builder.vertex(poseStack.last().pose(), size, size, 0.0F).color(r, g, b, a).uv(1.0F, 1.0F).uv2(15728880).endVertex();
            builder.vertex(poseStack.last().pose(), -size, size, 0.0F).color(r, g, b, a).uv(0.0F, 1.0F).uv2(15728880).endVertex();
        }
        else if (shape == DecayAnimationConfig.AuraShape.DIAMOND) {

            builder.vertex(poseStack.last().pose(), 0.0F, -size * 1.2F, 0.0F).color(r, g, b, a).uv(0.0F, 0.0F).uv2(15728880).endVertex();
            builder.vertex(poseStack.last().pose(), size * 1.2F, 0.0F, 0.0F).color(r, g, b, a).uv(1.0F, 0.0F).uv2(15728880).endVertex();
            builder.vertex(poseStack.last().pose(), 0.0F, size * 1.2F, 0.0F).color(r, g, b, a).uv(1.0F, 1.0F).uv2(15728880).endVertex();
            builder.vertex(poseStack.last().pose(), -size * 1.2F, 0.0F, 0.0F).color(r, g, b, a).uv(0.0F, 1.0F).uv2(15728880).endVertex();
        }
        else if (shape == DecayAnimationConfig.AuraShape.OCTAGON) {

            int segments = 8;
            float r_oct = size * 1.12F;
            for (int i = 0; i < segments; i++) {
                double angle1 = (i * 2 * Math.PI) / segments;
                double angle2 = ((i + 1) * 2 * Math.PI) / segments;

                float x1 = (float) (Math.cos(angle1) * r_oct);
                float y1 = (float) (Math.sin(angle1) * r_oct);
                float x2 = (float) (Math.cos(angle2) * r_oct);
                float y2 = (float) (Math.sin(angle2) * r_oct);

                builder.vertex(poseStack.last().pose(), 0.0F, 0.0F, 0.0F).color(r, g, b, a).uv(0.5F, 0.5F).uv2(15728880).endVertex();
                builder.vertex(poseStack.last().pose(), x1, y1, 0.0F).color(r, g, b, a).uv(x1/r_oct*0.5F + 0.5F, y1/r_oct*0.5F + 0.5F).uv2(15728880).endVertex();
                builder.vertex(poseStack.last().pose(), x2, y2, 0.0F).color(r, g, b, a).uv(x2/r_oct*0.5F + 0.5F, y2/r_oct*0.5F + 0.5F).uv2(15728880).endVertex();
                builder.vertex(poseStack.last().pose(), 0.0F, 0.0F, 0.0F).color(r, g, b, a).uv(0.5F, 0.5F).uv2(15728880).endVertex();
            }
        }

        poseStack.popPose();
    }

    public static void applyGlobalItemAnimation(PoseStack poseStack, ItemStack stack, ItemDisplayContext displayContext) {
        if (stack == null || stack.isEmpty()) return;

        DecayAnimationConfig config = DecayItemAnimationRegistry.getConfig(stack.getItem());

        if (config != null) {
            long time = System.currentTimeMillis();

            if (displayContext == ItemDisplayContext.GUI) {
                MultiBufferSource bufferSource = CURRENT_RENDER_BUFFER.get();
                renderGuiSlotAura(poseStack, bufferSource, time, config);
            }

            config.getAnimator().animate(poseStack, displayContext, stack, time);
        }
    }
}