package com.maxwell.hyperdamagelib.util;

import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DummyWatchdog {
    public static final Map<UUID, DummyData> ACTIVE_DUMMIES = new ConcurrentHashMap<>();

    public static class DummyData {
        public final UUID uuid;
        public final ResourceKey<Level> dimension;
        public final double x, y, z;
        public final float yRot, xRot;
        public final NonNullList<ItemStack> armor;
        public final NonNullList<ItemStack> hands;

        public DummyData(UUID uuid, ResourceKey<Level> dimension, double x, double y, double z, float yRot, float xRot, NonNullList<ItemStack> armor, NonNullList<ItemStack> hands) {
            this.uuid = uuid;
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yRot = yRot;
            this.xRot = xRot;
            this.armor = armor;
            this.hands = hands;
        }
    }
}