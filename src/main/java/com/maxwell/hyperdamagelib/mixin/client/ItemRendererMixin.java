package com.maxwell.hyperdamagelib.mixin.client;

import com.maxwell.hyperdamagelib.client.util.DecayAnimationConfig;
import com.maxwell.hyperdamagelib.client.util.DecayClientAnimationHelper;
import com.maxwell.hyperdamagelib.client.util.DecayItemAnimationRegistry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void decay$onRenderHead(ItemStack stack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight, int combinedOverlay, BakedModel model, CallbackInfo ci) {
        DecayClientAnimationHelper.CURRENT_RENDER_STACK.set(stack);
        DecayClientAnimationHelper.CURRENT_RENDER_CONTEXT.set(displayContext);
        DecayClientAnimationHelper.CURRENT_RENDER_BUFFER.set(bufferSource);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void decay$onRenderTail(ItemStack stack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight, int combinedOverlay, BakedModel model, CallbackInfo ci) {
        if (stack != null && !stack.isEmpty() && bufferSource != null) {
            DecayAnimationConfig config = DecayItemAnimationRegistry.getConfig(stack.getItem());
            if (config != null && config.hasAura()) {
                long time = System.currentTimeMillis();
                DecayClientAnimationHelper.renderGuiSlotAura(poseStack, bufferSource, time, config, displayContext);
            }
        }
        DecayClientAnimationHelper.CURRENT_RENDER_STACK.remove();
        DecayClientAnimationHelper.CURRENT_RENDER_CONTEXT.remove();
        DecayClientAnimationHelper.CURRENT_RENDER_BUFFER.remove();
    }
}