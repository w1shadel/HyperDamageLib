package com.maxwell.hyperdamagelib.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageEffects;
import net.minecraft.world.damagesource.DamageScaling;
import net.minecraft.world.damagesource.DamageType;
@SuppressWarnings("removal")
public class ModDamageTypes {

    public static final ResourceKey<DamageType> EROSION = register("erosion");

    public static final ResourceKey<DamageType> TRUE_VOID = register("true_void");

    private static ResourceKey<DamageType> register(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("hyperdamagelib", name));
    }

    public static void bootstrap(BootstapContext<DamageType> context) {

        context.register(EROSION, new DamageType("erosion", DamageScaling.ALWAYS, 0.1F, DamageEffects.HURT));
        context.register(TRUE_VOID, new DamageType("true_void", DamageScaling.ALWAYS, 0.1F, DamageEffects.HURT));
    }
}