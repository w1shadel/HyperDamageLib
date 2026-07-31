package com.maxwell.hyperdamagelib.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
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

    @Invoker("onEffectAdded")
    void invokeOnEffectAdded(net.minecraft.world.effect.MobEffectInstance effectInstance, net.minecraft.world.entity.Entity entity);

    @Invoker("onEffectUpdated")
    void invokeOnEffectUpdated(net.minecraft.world.effect.MobEffectInstance effectInstance, boolean forced, net.minecraft.world.entity.Entity entity);

    @Accessor("lastHurt")
    float getLastHurt();

    @Accessor("lastHurt")
    void setLastHurt(float value);

    @Accessor("lastDamageSource")
    void setLastDamageSource(DamageSource source);

    @Accessor("lastDamageStamp")
    void setLastDamageStamp(long value);

    @Accessor("lastHurtByPlayer")
    void setLastHurtByPlayer(Player player);

    @Accessor("lastHurtByPlayerTime")
    void setLastHurtByPlayerTime(int value);

    @Invoker("checkTotemDeathProtection")
    boolean invokeCheckTotemDeathProtection(DamageSource source);

    @Invoker("playHurtSound")
    void invokePlayHurtSound(DamageSource source);
}