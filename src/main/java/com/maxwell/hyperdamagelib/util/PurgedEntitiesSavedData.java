package com.maxwell.hyperdamagelib.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PurgedEntitiesSavedData extends SavedData {
    private static final String DATA_NAME = "hyperdamagelib_purged_entities";
    private final Set<UUID> purgedUuids = new HashSet<>();

    public static PurgedEntitiesSavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                PurgedEntitiesSavedData::load,
                PurgedEntitiesSavedData::new,
                DATA_NAME
        );
    }

    public static PurgedEntitiesSavedData load(CompoundTag tag) {
        PurgedEntitiesSavedData data = new PurgedEntitiesSavedData();
        ListTag uuidList = tag.getList("PurgedUUIDs", Tag.TAG_STRING);
        for (int i = 0; i < uuidList.size(); i++) {
            try {
                data.purgedUuids.add(UUID.fromString(uuidList.getString(i)));
            } catch (Exception ignored) {
            }
        }
        return data;
    }

    public void markPurged(UUID uuid) {
        if (uuid == null) return;
        this.purgedUuids.add(uuid);
        setDirty();
    }

    public void markPurged(Entity entity) {
        if (entity != null) {
            markPurged(entity.getUUID());
        }
    }

    public boolean isPurged(UUID uuid) {
        return uuid != null && this.purgedUuids.contains(uuid);
    }

    public boolean isPurged(Entity entity) {
        return entity != null && isPurged(entity.getUUID());
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag uuidList = new ListTag();
        for (UUID uuid : this.purgedUuids) {
            uuidList.add(StringTag.valueOf(uuid.toString()));
        }
        tag.put("PurgedUUIDs", uuidList);
        return tag;
    }
}