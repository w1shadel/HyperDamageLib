package com.maxwell.hyperdamagelib.mixin;


import com.maxwell.hyperdamagelib.util.IDecayEntity;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin extends Level {
    protected ServerLevelMixin(WritableLevelData p_270739_, ResourceKey<Level> p_270683_, RegistryAccess p_270200_, Holder<DimensionType> p_270240_, Supplier<ProfilerFiller> p_270692_, boolean p_270904_, boolean p_270470_, long p_270248_, int p_270466_) {
        super(p_270739_, p_270683_, p_270200_, p_270240_, p_270692_, p_270904_, p_270470_, p_270248_, p_270466_);
    }

    @Inject(method = "removePlayerImmediately", at = @At("HEAD"))
    public void removePlayerImmediatelyBeforeMixin(ServerPlayer player, Entity.RemovalReason removalReason, CallbackInfo ci) {
        if (player instanceof IDecayEntity decay) {
            decay.setRemoveBypass(true);
        }
    }

    @Inject(method = "removePlayerImmediately", at = @At("RETURN"))
    public void removePlayerImmediatelyAfterMixin(ServerPlayer player, Entity.RemovalReason removalReason, CallbackInfo ci) {
        if (player instanceof IDecayEntity decay) {
            decay.setRemoveBypass(false);
        }
    }
}