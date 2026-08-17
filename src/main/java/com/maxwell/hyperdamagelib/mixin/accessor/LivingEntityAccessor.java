package com.maxwell.hyperdamagelib.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("DATA_HEALTH_ID")
    static EntityDataAccessor<Float> getDataHealthId() {
        throw new AssertionError();
    }

    @Accessor("activeEffects")
    Map<MobEffect, MobEffectInstance> getActiveEffects();

    @Accessor("dead")
    boolean isDeadFlag();

    @Accessor("dead")
    void setDeadFlag(boolean dead);

    @Invoker("dropAllDeathLoot")
    void invokeDropAllDeathLoot(DamageSource source);

    @Invoker("onEffectAdded")
    void invokeOnEffectAdded(MobEffectInstance effectInstance, Entity entity);

    @Invoker("onEffectUpdated")
    void invokeOnEffectUpdated(MobEffectInstance effectInstance, boolean forced, Entity entity);

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