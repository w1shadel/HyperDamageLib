package com.maxwell.hyperdamagelib.mixin.accessor;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Entity.class)
public interface EntityAccessor {
    @Accessor("invulnerableTime")
    int getInvulnerableTime();

    @Accessor("invulnerableTime")
    void setInvulnerableTime(int value);
}