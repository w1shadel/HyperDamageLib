package com.maxwell.hyperdamagelib.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class DecayMobEffect extends MobEffect {
    protected DecayMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public List<ItemStack> getCurativeItems() {
        return List.of();
    }
}