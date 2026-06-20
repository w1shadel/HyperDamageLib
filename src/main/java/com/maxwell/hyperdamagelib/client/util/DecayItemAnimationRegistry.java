package com.maxwell.hyperdamagelib.client.util;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public final class DecayItemAnimationRegistry {

    @FunctionalInterface
    public interface IItemAnimator {
        void animate(GuiGraphics guiGraphics, int slotIndex, ItemStack stack, long time);
    }

    private static final Map<Item, IItemAnimator> REGISTRY = new HashMap<>();


    private DecayItemAnimationRegistry() {}

    public static void register(Item item, IItemAnimator animator) {
        if (item != null && animator != null) {
            REGISTRY.put(item, animator);
        }
    }

    public static IItemAnimator getAnimator(Item item) {
        return REGISTRY.get(item);
    }

    public static boolean hasAnimator(Item item) {
        return REGISTRY.containsKey(item);
    }
}