package com.maxwell.hyperdamagelib.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("DATA_HEALTH_ID")
    static EntityDataAccessor<Float> getDataHealthId() {
        throw new AssertionError();
    }

    @Accessor("dead")
    boolean isDeadFlag();

    @Accessor("dead")
    void setDeadFlag(boolean dead);

    @Invoker("dropAllDeathLoot")
    void invokeDropAllDeathLoot(DamageSource source);

    @Invoker("getDamageAfterArmorAbsorb")
    float invokeGetDamageAfterArmorAbsorb(DamageSource source, float amount);

    @Invoker("getDamageAfterMagicAbsorb")
    float invokeGetDamageAfterMagicAbsorb(DamageSource source, float amount);
}