package com.maxwell.hyperdamagelib.client.util;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class DecayItemAnimationRegistry {

    @FunctionalInterface
    public interface IItemAnimator {
        void animate(PoseStack poseStack, ItemDisplayContext displayContext, ItemStack stack, long time);
    }

    private static final Map<ResourceLocation, DecayAnimationConfig> REGISTRY = new HashMap<>();

    private DecayItemAnimationRegistry() {}

    
    public static void register(Item item, DecayAnimationConfig config) {
        if (item != null && config != null) {
            ResourceLocation registryName = BuiltInRegistries.ITEM.getKey(item);
            if (registryName != null) {
                REGISTRY.put(registryName, config);
            }
        }
    }

    
    public static void register(Item item, IItemAnimator animator) {
        if (item != null && animator != null) {
            register(item, DecayAnimationConfig.builder().animator(animator).aura(false).build());
        }
    }

    public static DecayAnimationConfig getConfig(Item item) {
        if (item == null) return null;
        ResourceLocation registryName = BuiltInRegistries.ITEM.getKey(item);
        return registryName != null ? REGISTRY.get(registryName) : null;
    }

    public static boolean hasAnimator(Item item) {
        if (item == null) return false;
        ResourceLocation registryName = BuiltInRegistries.ITEM.getKey(item);
        return registryName != null && REGISTRY.containsKey(registryName);
    }
}