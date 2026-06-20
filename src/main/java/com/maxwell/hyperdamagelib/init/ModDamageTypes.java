package com.maxwell.hyperdamagelib.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

@SuppressWarnings("removal")
public class ModDamageTypes {
    public static final ResourceKey<DamageType> EROSION = register("erosion");
    public static final ResourceKey<DamageType> VOID_SHRED = register("void_shred");

    private static ResourceKey<DamageType> register(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("hyperdamagelib", name));
    }

}