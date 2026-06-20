package com.maxwell.hyperdamagelib.client.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class DecayClientAnimationHelper {
    public static final ThreadLocal<ItemStack> CURRENT_RENDER_STACK = ThreadLocal.withInitial(() -> null);
    public static final ThreadLocal<ItemDisplayContext> CURRENT_RENDER_CONTEXT = ThreadLocal.withInitial(() -> null);
    public static final ThreadLocal<MultiBufferSource> CURRENT_RENDER_BUFFER = ThreadLocal.withInitial(() -> null);

    private static void writeVertex(VertexConsumer builder, PoseStack.Pose pose, float x, float y, float z, int r, int g, int b, int a) {
        builder.vertex(pose.pose(), x, y, z)
                .color(r, g, b, a)
                .endVertex();
    }

    private static void applyBillboard(PoseStack poseStack) {
        org.joml.Matrix4f matrix = poseStack.last().pose();
        float sx = (float) Math.sqrt(matrix.m00() * matrix.m00() + matrix.m10() * matrix.m10() + matrix.m20() * matrix.m20());
        float sy = (float) Math.sqrt(matrix.m01() * matrix.m01() + matrix.m11() * matrix.m11() + matrix.m21() * matrix.m21());
        float sz = (float) Math.sqrt(matrix.m02() * matrix.m02() + matrix.m12() * matrix.m12() + matrix.m22() * matrix.m22());
        matrix.m00(sx);
        matrix.m01(0.0F);
        matrix.m02(0.0F);
        matrix.m10(0.0F);
        matrix.m11(sy);
        matrix.m12(0.0F);
        matrix.m20(0.0F);
        matrix.m21(0.0F);
        matrix.m22(sz);
    }

    public static void renderGuiSlotAura(PoseStack poseStack, MultiBufferSource bufferSource, long time, DecayAnimationConfig config, ItemDisplayContext displayContext) {
        if (bufferSource == null || config == null || !config.hasAura()) return;
        boolean isGui = (displayContext == ItemDisplayContext.GUI);
        poseStack.pushPose();
        float zOffset = isGui ? -0.05F : 0.0F;
        poseStack.translate(0.0F, 0.0F, zOffset);
        double elapsedSeconds = (time % 3600000L) / 1000.0;
        float pSpeed = config.getAuraPulseSpeed();
        float pAmp = config.getAuraPulseAmplitude();
        float pulseSize = (float) (Math.sin(elapsedSeconds * pSpeed) * pAmp + (1.0F - pAmp));
        float rSpeed = config.getAuraRotateSpeed();
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees((float) ((elapsedSeconds * rSpeed) % 360.0)));
        poseStack.scale(pulseSize, pulseSize, 1.0F);
        int r = config.getRed();
        int g = config.getGreen();
        int b = config.getBlue();
        int a = config.getAlpha();
        float size = config.getAuraScale();
        RenderType renderType = isGui ? RenderType.gui() : RenderType.debugFilledBox();
        VertexConsumer builder = bufferSource.getBuffer(renderType);
        DecayAnimationConfig.AuraShape shape = config.getAuraShape();
        renderAuraShape(builder, poseStack, shape, size, r, g, b, a, isGui, 0);
        if (!isGui && shape == DecayAnimationConfig.AuraShape.BLACK_HOLE) {
            poseStack.pushPose();
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90.0F));
            renderAuraShape(builder, poseStack, shape, size, r, g, b, a, isGui, 1);
            poseStack.popPose();
            poseStack.pushPose();
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90.0F));
            renderAuraShape(builder, poseStack, shape, size, r, g, b, a, isGui, 2);
            poseStack.popPose();
        }
        poseStack.popPose();
        if (config.hasSmoke()) {
            renderSmokePuffs(poseStack, bufferSource, time, config, displayContext);
        }
    }

    private static void renderAuraShape(VertexConsumer builder, PoseStack poseStack, DecayAnimationConfig.AuraShape shape, float size, int r, int g, int b, int a, boolean isGui, int semiCircleMode) {
        if (shape == DecayAnimationConfig.AuraShape.SQUARE) {
            writeVertex(builder, poseStack.last(), -size, -size, 0.0F, r, g, b, a);
            writeVertex(builder, poseStack.last(), size, -size, 0.0F, r, g, b, a);
            writeVertex(builder, poseStack.last(), size, size, 0.0F, r, g, b, a);
            writeVertex(builder, poseStack.last(), -size, size, 0.0F, r, g, b, a);
        } else if (shape == DecayAnimationConfig.AuraShape.DIAMOND) {
            writeVertex(builder, poseStack.last(), 0.0F, -size * 1.2F, 0.0F, r, g, b, a);
            writeVertex(builder, poseStack.last(), size * 1.2F, 0.0F, 0.0F, r, g, b, a);
            writeVertex(builder, poseStack.last(), 0.0F, size * 1.2F, 0.0F, r, g, b, a);
            writeVertex(builder, poseStack.last(), -size * 1.2F, 0.0F, 0.0F, r, g, b, a);
        } else if (shape == DecayAnimationConfig.AuraShape.OCTAGON) {
            int segments = 8;
            float r_oct = size * 1.12F;
            for (int i = 0; i < segments; i++) {
                double angle1 = (i * 2 * Math.PI) / segments;
                double angle2 = ((i + 1) * 2 * Math.PI) / segments;
                float x1 = (float) (Math.cos(angle1) * r_oct);
                float y1 = (float) (Math.sin(angle1) * r_oct);
                float x2 = (float) (Math.cos(angle2) * r_oct);
                float y2 = (float) (Math.sin(angle2) * r_oct);
                writeVertex(builder, poseStack.last(), 0.0F, 0.0F, 0.0F, r, g, b, a);
                writeVertex(builder, poseStack.last(), x1, y1, 0.0F, r, g, b, a);
                writeVertex(builder, poseStack.last(), x2, y2, 0.0F, r, g, b, a);
                writeVertex(builder, poseStack.last(), 0.0F, 0.0F, 0.0F, r, g, b, a);
            }
        } else if (shape == DecayAnimationConfig.AuraShape.BLACK_HOLE) {
            int segments = 16;
            float r_inner = size * 0.8F;
            float r_outer = size * 1.2F;
            int centerR = 0;
            int centerG = 0;
            int centerB = 0;
            int centerA = isGui ? 255 : 0;
            int ringR = 0;
            int ringG = 0;
            int ringB = 0;
            int ringA = isGui ? 255 : 180;
            int edgeR = r;
            int edgeG = g;
            int edgeB = b;
            int edgeA = a;
            for (int i = 0; i < segments; i++) {
                double angle1 = (i * 2 * Math.PI) / segments;
                double angle2 = ((i + 1) * 2 * Math.PI) / segments;
                if (semiCircleMode == 1) {
                    if (Math.cos(angle1) > 0.01 || Math.cos(angle2) > 0.01) continue;
                }
                if (semiCircleMode == 2) {
                    if (Math.sin(angle1) > 0.01 || Math.sin(angle2) > 0.01) continue;
                }
                float cos1 = (float) Math.cos(angle1);
                float sin1 = (float) Math.sin(angle1);
                float cos2 = (float) Math.cos(angle2);
                float sin2 = (float) Math.sin(angle2);
                float ix1 = cos1 * r_inner;
                float iy1 = sin1 * r_inner;
                float ix2 = cos2 * r_inner;
                float iy2 = sin2 * r_inner;
                writeVertex(builder, poseStack.last(), 0.0F, 0.0F, 0.0F, centerR, centerG, centerB, centerA);
                writeVertex(builder, poseStack.last(), ix1, iy1, 0.0F, ringR, ringG, ringB, ringA);
                writeVertex(builder, poseStack.last(), ix2, iy2, 0.0F, ringR, ringG, ringB, ringA);
                writeVertex(builder, poseStack.last(), 0.0F, 0.0F, 0.0F, centerR, centerG, centerB, centerA);
                float ox1 = cos1 * r_outer;
                float oy1 = sin1 * r_outer;
                float ox2 = cos2 * r_outer;
                float oy2 = sin2 * r_outer;
                writeVertex(builder, poseStack.last(), ix1, iy1, 0.0F, ringR, ringG, ringB, ringA);
                writeVertex(builder, poseStack.last(), ox1, oy1, 0.0F, edgeR, edgeG, edgeB, edgeA);
                writeVertex(builder, poseStack.last(), ox2, oy2, 0.0F, edgeR, edgeG, edgeB, edgeA);
                writeVertex(builder, poseStack.last(), ix2, iy2, 0.0F, ringR, ringG, ringB, ringA);
            }
        }
    }

    private static void renderSmokePuffs(PoseStack poseStack, MultiBufferSource bufferSource, long time, DecayAnimationConfig config, ItemDisplayContext displayContext) {
        int numPuffs = config.getSmokeCount();
        long loopDuration = config.getSmokeLifetime();
        float smokeScaleMultiplier = config.getSmokeSize();
        float size = config.getAuraScale();
        int baseR = config.getRed();
        int baseG = config.getGreen();
        int baseB = config.getBlue();
        int baseA = config.getAlpha();
        int smokeMaxAlpha = Math.max(baseA, 150);
        boolean isGui = (displayContext == ItemDisplayContext.GUI);
        RenderType renderType = isGui ? RenderType.gui() : RenderType.debugFilledBox();
        VertexConsumer builder = bufferSource.getBuffer(renderType);
        for (int i = 0; i < numPuffs; i++) {
            long offset = i * (loopDuration / numPuffs);
            double progress = ((time + offset) % loopDuration) / (double) loopDuration;
            double baseAngle = i * (2.0 * Math.PI) / numPuffs;
            double slowRotation = (time % 3600000L) / 8000.0 * (2.0 * Math.PI);
            double angle = baseAngle + slowRotation;
            float maxDrift = size * 2.8F;
            float driftX = (float) (Math.cos(angle) * progress * maxDrift);
            float driftY = (float) (Math.sin(angle) * progress * maxDrift);
            float swayAmp = (float) (Math.sin(progress * Math.PI * 4.0 + i) * size * 0.18F * progress);
            double swayAngle = angle + (Math.PI / 2.0);
            driftX += (float) (Math.cos(swayAngle) * swayAmp);
            driftY += (float) (Math.sin(swayAngle) * swayAmp);
            float scale;
            if (progress < 0.2) {
                scale = (float) (progress / 0.2) * size * 1.30F;
            } else {
                scale = (float) (1.0 - (progress - 0.2) / 0.8) * size * 1.30F;
            }
            scale *= smokeScaleMultiplier;
            scale *= (1.0F + 0.15F * (float) Math.sin(i * 1.2));
            float rotation = (float) (progress * 120.0F + i * 45.0F);
            int r = (int) (baseR * 0.3F);
            int g = (int) (baseG * 0.3F);
            int b = (int) (baseB * 0.3F);
            float alphaFactor = 1.0F;
            if (progress > 0.4) {
                alphaFactor = (float) (1.0 - (progress - 0.4) / 0.6);
            }
            int a = (int) (smokeMaxAlpha * alphaFactor * 0.75F);
            poseStack.pushPose();
            float smokeZ = isGui ? -0.04F : 0.0F;
            poseStack.translate(driftX, driftY, smokeZ);
            if (!isGui) {
                applyBillboard(poseStack);
            }
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(rotation));
            poseStack.scale(scale, scale, 1.0F);
            int segments = 8;
            float r_puff = 0.8F;
            for (int s = 0; s < segments; s++) {
                double angle1 = (s * 2 * Math.PI) / segments;
                double angle2 = ((s + 1) * 2 * Math.PI) / segments;
                float px1 = (float) (Math.cos(angle1) * r_puff);
                float py1 = (float) (Math.sin(angle1) * r_puff);
                float px2 = (float) (Math.cos(angle2) * r_puff);
                float py2 = (float) (Math.sin(angle2) * r_puff);
                writeVertex(builder, poseStack.last(), 0.0F, 0.0F, 0.0F, r, g, b, a);
                writeVertex(builder, poseStack.last(), px1, py1, 0.0F, r, g, b, 0);
                writeVertex(builder, poseStack.last(), px2, py2, 0.0F, r, g, b, 0);
                writeVertex(builder, poseStack.last(), 0.0F, 0.0F, 0.0F, r, g, b, a);
            }
            poseStack.popPose();
        }
    }

    public static void applyGlobalItemAnimation(PoseStack poseStack, ItemStack stack, ItemDisplayContext displayContext) {
        if (stack == null || stack.isEmpty()) return;
        DecayAnimationConfig config = DecayItemAnimationRegistry.getConfig(stack.getItem());
        if (config != null) {
            long time = System.currentTimeMillis();
            MultiBufferSource bufferSource = CURRENT_RENDER_BUFFER.get();
            if (bufferSource != null) {
                renderGuiSlotAura(poseStack, bufferSource, time, config, displayContext);
            }
            config.getAnimator().animate(poseStack, displayContext, stack, time);
        }
    }
}