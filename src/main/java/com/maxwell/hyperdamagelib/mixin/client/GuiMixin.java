package com.maxwell.hyperdamagelib.mixin.client;

import com.maxwell.hyperdamagelib.client.util.DecayClientEffectHelper;
import com.maxwell.hyperdamagelib.client.util.DecayItemAnimationRegistry;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {

    @Shadow private int screenWidth;
    @Shadow private int screenHeight;

    @Unique
    private static long csp$lastLogTime = 0;

    @Inject(method = "renderSlot", at = @At("HEAD"))
    private void csp$onRenderSlotHead(GuiGraphics guiGraphics, int x, int y, float partialTick, Player player, ItemStack stack, int slotIndex, CallbackInfo ci) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        // デバッグログ：Mixinが動いているかを1秒に1回コンソールに出力
        long currentTime = System.currentTimeMillis();
        if (currentTime - csp$lastLogTime > 1000) {
            csp$lastLogTime = currentTime;
            System.out.println("[HDL-DEBUG] GuiMixin renderSlot is executing! Target item: " + stack.getItem().getDescriptionId());
        }

        boolean needsAnimation = Math.abs(DecayClientEffectHelper.currentScaleX - 1.0F) > 0.005F
                || Math.abs(DecayClientEffectHelper.currentScaleY - 1.0F) > 0.005F
                || Math.abs(DecayClientEffectHelper.currentSpinSpeed) > 0.01F
                || Math.abs(DecayClientEffectHelper.currentWaveAmplitude) > 0.01F;

        DecayItemAnimationRegistry.IItemAnimator animator = DecayItemAnimationRegistry.getAnimator(stack.getItem());

        if (needsAnimation || animator != null) {
            guiGraphics.pose().pushPose();

            float centerX = x + 8.0F;
            float centerY = y + 8.0F;

            guiGraphics.pose().translate(centerX, centerY, 0.0F);

            long time = System.currentTimeMillis();

            if (needsAnimation) {
                float angle = DecayClientEffectHelper.accumulatedRotation + (slotIndex * 30.0F);
                if (slotIndex % 2 == 0) {
                    angle = -angle;
                }

                float floatOffset = 0.0F;
                if (DecayClientEffectHelper.currentWaveAmplitude > 0.01F) {
                    double speed = DecayClientEffectHelper.currentWaveSpeed == 0 ? 0.005D : DecayClientEffectHelper.currentWaveSpeed;
                    floatOffset = (float) Math.sin((time * speed) + slotIndex) * DecayClientEffectHelper.currentWaveAmplitude;
                }

                guiGraphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(angle));
                guiGraphics.pose().translate(-centerX, -centerY + floatOffset, 0.0F);
                guiGraphics.pose().scale(DecayClientEffectHelper.currentScaleX, DecayClientEffectHelper.currentScaleY, 1.0F);
            }
            else {
                animator.animate(guiGraphics, slotIndex, stack, time);
                guiGraphics.pose().translate(-centerX, -centerY, 0.0F);
            }
        }
    }

    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void csp$onRenderSlotTail(GuiGraphics guiGraphics, int x, int y, float partialTick, Player player, ItemStack stack, int slotIndex, CallbackInfo ci) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        boolean needsAnimation = Math.abs(DecayClientEffectHelper.currentScaleX - 1.0F) > 0.005F
                || Math.abs(DecayClientEffectHelper.currentScaleY - 1.0F) > 0.005F
                || Math.abs(DecayClientEffectHelper.currentSpinSpeed) > 0.01F
                || Math.abs(DecayClientEffectHelper.currentWaveAmplitude) > 0.01F;

        boolean hasCustomAnimator = DecayItemAnimationRegistry.hasAnimator(stack.getItem());

        if (needsAnimation || hasCustomAnimator) {
            guiGraphics.pose().popPose();
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void csp$onRenderTail(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        if (DecayClientEffectHelper.glitchTicks > 0 || DecayClientEffectHelper.glitchTicks == -1) {
            long time = System.currentTimeMillis();
            float intensity = DecayClientEffectHelper.glitchTicks == -1 ? 1.0F : DecayClientEffectHelper.glitchIntensity;
            float noise = (float) (Math.sin(time * 0.07D) * 0.6D + 0.4D);
            int alpha = (int) (18 * intensity * noise);

            if (alpha > 0) {
                int color = (alpha << 24) | (180 << 16) | (0 << 8) | 220;
                guiGraphics.fill(0, 0, this.screenWidth, this.screenHeight, color);
            }
        }
    }
}
