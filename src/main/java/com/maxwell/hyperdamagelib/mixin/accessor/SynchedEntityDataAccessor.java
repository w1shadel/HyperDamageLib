package com.maxwell.hyperdamagelib.mixin.accessor;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SynchedEntityData.class)
public interface SynchedEntityDataAccessor {

    @Accessor("itemsById")
    Int2ObjectMap<SynchedEntityData.DataItem<?>> getItemsById();

    @Invoker("getItem")
    <T> SynchedEntityData.DataItem<T> invokeGetItem(EntityDataAccessor<T> key);

    @Accessor("isDirty")
    void setIsDirty(boolean dirty);
}