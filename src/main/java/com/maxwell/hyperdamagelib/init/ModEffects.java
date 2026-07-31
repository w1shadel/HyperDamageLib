package com.maxwell.hyperdamagelib.init;

import com.maxwell.hyperdamagelib.HDL;
import com.maxwell.hyperdamagelib.effect.HealingSicknessEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, HDL.MODID);
    public static final RegistryObject<MobEffect> HEALING_SICKNESS = EFFECTS.register("healing_sickness",
            HealingSicknessEffect::new
    );
}