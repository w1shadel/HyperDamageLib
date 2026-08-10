package com.maxwell.hyperdamagelib.init;

import com.maxwell.hyperdamagelib.HDL;
import com.maxwell.hyperdamagelib.entity.MeasurementDummyEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, HDL.MODID);
    public static final RegistryObject<EntityType<MeasurementDummyEntity>> MEASUREMENT_DUMMY = ENTITY_TYPES.register("measurement_dummy",
            () -> EntityType.Builder.of(MeasurementDummyEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .build(HDL.MODID + ":measurement_dummy")
    );

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}