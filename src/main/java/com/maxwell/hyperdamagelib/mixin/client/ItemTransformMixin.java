package com.maxwell.hyperdamagelib.mixin.client;

import com.maxwell.hyperdamagelib.client.util.DecayClientAnimationHelper;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemTransform.class)
public class ItemTransformMixin {

    @Inject(method = "apply", at = @At("TAIL"))
    private void csp$onApplyTail(boolean leftHand, PoseStack poseStack, CallbackInfo ci) {
        ItemStack stack = DecayClientAnimationHelper.CURRENT_RENDER_STACK.get();
        ItemDisplayContext displayContext = DecayClientAnimationHelper.CURRENT_RENDER_CONTEXT.get();

        if (stack != null && !stack.isEmpty() && displayContext != null) {


            DecayClientAnimationHelper.applyGlobalItemAnimation(poseStack, stack, displayContext);
        }
    }
}