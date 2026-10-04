package com.maxwell.hyperdamagelib.util;

import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.*;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.ModFileScanData;
import net.minecraftforge.forgespi.locating.IModFile;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DecayForceKillHelper {
    private static final AABB COLLAPSED_AABB = new AABB(0.0, -9999.0, 0.0, 0.0, -9999.0, 0.0);

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || entity instanceof Player) return;
        if (entity.level() instanceof ServerLevel serverLevel) {
            PurgedEntitiesSavedData data = PurgedEntitiesSavedData.get(serverLevel);
            if (data != null && data.isPurged(entity.getUUID())) {
                event.setCanceled(true);
                entity.discard();
            }
        }
    }

    public static void decayForceKill(LivingEntity entity) {
        decayForceKill(entity, DecayDamageUtil.getErosionSource(entity.level(), entity));
    }

    public static void decayForceKill(@NotNull LivingEntity entity, DamageSource source) {
        if (entity.level().isClientSide()) return;
        if (entity instanceof ServerPlayer serverPlayer) {
            executePlayerKillHierarchy(serverPlayer, source);
            return;
        }

        DecayDamageUtil.markPermanentlyKilled(entity);
        try (var ignored1 = DecayDamageUtil.forceKillScope(entity)) {

            Class<?> entityClass = entity.getClass();
            String className = entityClass.getName();
            boolean isCustomModEntity = isCustomModClass(className);

            Set<UUID> targetUuids = isCustomModEntity ? extractAllLinkedUuids(entity) : Collections.singleton(entity.getUUID());
            ClassLoader targetLoader = entityClass.getClassLoader();
            Package targetPkg = entityClass.getPackage();
            String pkgPrefix = isCustomModEntity && targetPkg != null ? getDomainRootPackage(targetPkg.getName()) : "";

            if (isCustomModEntity) {
                if (entity.level() instanceof ServerLevel serverLevel) {
                    MinecraftServer server = serverLevel.getServer();

                    PurgedEntitiesSavedData.get(serverLevel).markPurged(entity);
                    for (UUID u : targetUuids) {
                        PurgedEntitiesSavedData.get(serverLevel).markPurged(u);
                    }

                    if (!pkgPrefix.isEmpty()) {
                        purgeSavedDataGeneric(server, targetUuids, targetLoader, pkgPrefix);
                    }
                }
            }

            try { purgeBossBars(entity, entity.level()); } catch (Throwable ignored) {}
            try { cascadeKillLinkedEntities(entity, source, targetUuids); } catch (Throwable ignored) {}

            try (var ignored2 = DecayDamageUtil.bypassScope(entity)) {
                entity.setHealth(0.0F);
                try {
                    entity.getEntityData().set(LivingEntityAccessor.getDataHealthId(), 0.0F);
                } catch (Throwable ignored) {}
            } catch (Throwable ignored) {}

            try { collapseBoundingBoxAndDimensions(entity); } catch (Throwable ignored) {}
            try { entity.die(source); } catch (Throwable ignored) {}
            try { dropAllForce(entity); } catch (Throwable ignored) {}

            if (entity.level() instanceof ServerLevel serverLevel) {
                for (Entity e : serverLevel.getAllEntities()) {
                    if (e != null && e.getClass() == entityClass && !(e instanceof Player)) {
                        setRemovalStateDirect(e);
                        e.discard();
                        serverLevel.getChunkSource().removeEntity(e);
                        serverLevel.entityTickList.remove(e);
                    }
                }
            }

            if (isCustomModEntity) {
                try { purgeStaticDataInDomain(entityClass, targetUuids, targetLoader, pkgPrefix); } catch (Throwable ignored) {}
                try { purgeFromExternalLists(entity); } catch (Throwable ignored) {}
            }

            try { wipeEntireEntityState(entity); } catch (Throwable ignored) {}

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean isCustomModClass(String className) {
        if (className == null) return false;
        return !className.startsWith("net.minecraft.") &&
                !className.startsWith("net.minecraftforge.") &&
                !className.startsWith("com.mojang.") &&
                !className.startsWith("java.") &&
                !className.startsWith("javax.") &&
                !className.startsWith("jdk.") &&
                !className.startsWith("sun.") &&
                !className.startsWith("org.spongepowered.") &&
                !className.startsWith("cpw.mods.") &&
                !className.startsWith("com.maxwell.hyperdamagelib.");
    }

    public static String getDomainRootPackage(String fullPackageName) {
        if (fullPackageName == null || !isCustomModClass(fullPackageName)) return "";
        String[] parts = fullPackageName.split("\\.");
        if (parts.length >= 3) {
            return parts[0] + "." + parts[1] + "." + parts[2];
        } else if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        }
        return fullPackageName;
    }

    public static void purgeSavedDataGeneric(MinecraftServer server, Set<UUID> targetUuids, ClassLoader loader, String pkgPrefix) {
        if (server == null || pkgPrefix.isEmpty()) return;

        for (ServerLevel level : server.getAllLevels()) {
            try {
                DimensionDataStorage storage = level.getDataStorage();
                Field cacheField = getFieldByType(storage.getClass(), Map.class);
                if (cacheField == null) continue;
                cacheField.setAccessible(true);
                Map<?, ?> cache = (Map<?, ?>) cacheField.get(storage);
                if (cache == null) continue;

                for (Object savedDataObj : cache.values()) {
                    if (savedDataObj == null) continue;
                    boolean modified = purgeMapsAndCollectionsInObject(savedDataObj, targetUuids, loader, pkgPrefix, server);
                    if (modified && savedDataObj instanceof net.minecraft.world.level.saveddata.SavedData sd) {
                        sd.setDirty();
                    }
                }
            } catch (Throwable ignored) {}
        }

        Set<Class<?>> domainClasses = new HashSet<>();
        collectReferencedDomainClasses(loader != null ? Object.class : null, domainClasses, pkgPrefix);

        for (IModFileInfo fileInfo : ModList.get().getModFiles()) {
            IModFile modFile = fileInfo.getFile();
            if (modFile == null) continue;
            try {
                ModFileScanData scanData = modFile.getScanResult();
                if (scanData != null && scanData.getClasses() != null) {
                    for (ModFileScanData.ClassData cd : scanData.getClasses()) {
                        String cname = cd.clazz().getClassName();
                        if (cname.startsWith(pkgPrefix)) {
                            tryDirectDomainDataPurge(cname, server, targetUuids, loader, pkgPrefix);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
    }
    private static void tryDirectDomainDataPurge(String className, MinecraftServer server, Set<UUID> targetUuids, ClassLoader loader, String pkgPrefix) {
        try {
            Class<?> clazz = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            for (Method m : clazz.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) continue;
                Class<?>[] params = m.getParameterTypes();

                Object dataObj = null;
                if (params.length == 1 && params[0] == MinecraftServer.class) {
                    m.setAccessible(true);
                    dataObj = m.invoke(null, server);
                } else if (params.length == 1 && params[0] == ServerLevel.class) {
                    m.setAccessible(true);
                    dataObj = m.invoke(null, server.overworld());
                }

                if (dataObj != null) {
                    boolean modified = purgeMapsAndCollectionsInObject(dataObj, targetUuids, loader, pkgPrefix, server);
                    if (modified && dataObj instanceof net.minecraft.world.level.saveddata.SavedData sd) {
                        sd.setDirty();
                    }
                }
            }
        } catch (Throwable ignored) {}
    }
    private static boolean purgeMapsAndCollectionsInObject(Object target, Set<UUID> targetUuids, ClassLoader loader, String pkgPrefix, MinecraftServer server) {
        if (target == null || pkgPrefix.isEmpty()) return false;
        boolean modified = false;
        Class<?> clazz = target.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    Object val = f.get(target);
                    if (val == null) continue;
                    if (BossEvent.class.isAssignableFrom(f.getType()) || val instanceof BossEvent) {
                        purgeSingleBossEvent(val, server);
                        f.set(target, null);
                        modified = true;
                        continue;
                    }
                    if (val instanceof Map<?, ?> map) {
                        int sizeBefore = map.size();
                        for (Object entryVal : map.values()) {
                            if (entryVal != null && entryVal.getClass().getName().startsWith(pkgPrefix)) {
                                purgeBossBarsFromObjectFields(entryVal, server);
                            }
                        }
                        map.entrySet().removeIf(entry -> {
                            Object v = entry.getValue();
                            return v != null && v.getClass().getName().startsWith(pkgPrefix);
                        });
                        if (map.size() != sizeBefore) modified = true;

                    } else if (val instanceof Collection<?> col) {
                        int sizeBefore = col.size();
                        for (Object item : col) {
                            if (item != null && item.getClass().getName().startsWith(pkgPrefix)) {
                                purgeBossBarsFromObjectFields(item, server);
                            }
                        }
                        col.removeIf(item -> item != null && item.getClass().getName().startsWith(pkgPrefix));
                        if (col.size() != sizeBefore) modified = true;
                    }
                } catch (Throwable ignored) {
                }
            }
            clazz = clazz.getSuperclass();
        }
        return modified;
    }

    public static void purgeStaticDataInDomain(Class<?> startClass, Set<UUID> targetUuids, ClassLoader loader, String pkgPrefix) {
        if (pkgPrefix == null || pkgPrefix.isEmpty()) return;

        for (IModFileInfo fileInfo : ModList.get().getModFiles()) {
            IModFile modFile = fileInfo.getFile();
            if (modFile == null) continue;
            try {
                ModFileScanData scanData = modFile.getScanResult();
                if (scanData != null && scanData.getClasses() != null) {
                    for (ModFileScanData.ClassData cd : scanData.getClasses()) {
                        String cname = cd.clazz().getClassName();
                        if (cname.startsWith(pkgPrefix)) {
                            wipeStaticMapsInClass(cname, targetUuids, pkgPrefix);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
    }
    private static void wipeStaticMapsInClass(String className, Set<UUID> targetUuids, String pkgPrefix) {
        try {
            Class<?> clazz = Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            for (Field f : clazz.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    Object staticObj = f.get(null);

                    if (staticObj instanceof Map<?, ?> map) {
                        map.clear();
                    } else if (staticObj instanceof Collection<?> col) {
                        forceWipeArrayList(col);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }
    private static void collectReferencedDomainClasses(Class<?> targetClass, Set<Class<?>> scanned, String pkgPrefix) {
        if (targetClass == null || scanned.contains(targetClass)) return;
        if (!targetClass.getName().startsWith(pkgPrefix)) return;
        scanned.add(targetClass);
        for (Field f : targetClass.getDeclaredFields()) {
            collectReferencedDomainClasses(f.getType(), scanned, pkgPrefix);
        }
        for (Method m : targetClass.getDeclaredMethods()) {
            collectReferencedDomainClasses(m.getReturnType(), scanned, pkgPrefix);
            for (Class<?> p : m.getParameterTypes()) {
                collectReferencedDomainClasses(p, scanned, pkgPrefix);
            }
        }
        for (Class<?> nested : targetClass.getDeclaredClasses()) {
            collectReferencedDomainClasses(nested, scanned, pkgPrefix);
        }
        collectReferencedDomainClasses(targetClass.getSuperclass(), scanned, pkgPrefix);
    }

    private static void cascadeKillLinkedEntities(LivingEntity avatar, DamageSource source, Set<UUID> targetUuids) {
        if (!(avatar.level() instanceof ServerLevel serverLevel)) return;
        MinecraftServer server = serverLevel.getServer();
        for (UUID uuid : targetUuids) {
            if (uuid.equals(avatar.getUUID())) continue;
            for (ServerLevel level : server.getAllLevels()) {
                Entity linkedEntity = level.getEntity(uuid);
                if (linkedEntity instanceof Player) continue;
                if (linkedEntity instanceof LivingEntity living && !living.isRemoved()) {
                    decayForceKill(living, source);
                }
            }
        }
    }

    public static void purgeBossBars(Object target, Level level) {
        if (target == null) return;
        MinecraftServer server = level instanceof ServerLevel sl ? sl.getServer() : null;
        purgeBossBarsFromObjectFields(target, server);
    }

    public static void wipeEntireEntityState(LivingEntity entity) {
        if (entity == null || entity instanceof Player) return;
        try {
            CompoundTag customTag = entity.getPersistentData();
            if (customTag != null) {
                for (String key : customTag.getAllKeys().toArray(new String[0])) {
                    customTag.remove(key);
                }
            }
            entity.getTags().clear();
            AttributeMap attributes = entity.getAttributes();
            if (attributes != null) {
                for (AttributeInstance instance : attributes.getSyncableAttributes()) {
                    instance.removeModifiers();
                    instance.setBaseValue(0.0D);
                }
            }
            entity.invalidateCaps();
            breakBrain(entity);
            neutralizeEntityFields(entity);
        } catch (Throwable ignored) {
        }
    }

    public static void collapseBoundingBoxAndDimensions(Entity entity) {
        if (entity == null) return;
        try {
            entity.setBoundingBox(COLLAPSED_AABB);
            Field dimensionsField = getField(Entity.class, "f_19815_", "dimensions");
            if (dimensionsField != null) {
                dimensionsField.setAccessible(true);
                dimensionsField.set(entity, EntityDimensions.scalable(0.0F, 0.0F));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void setRemovalStateDirect(Entity entity) {
        try {
            Field removalField = getField(Entity.class, "f_19853_", "removalReason");
            if (removalField != null) {
                removalField.setAccessible(true);
                removalField.set(entity, Entity.RemovalReason.KILLED);
            }
        } catch (Throwable t) {
            try {
                entity.remove(Entity.RemovalReason.KILLED);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void executePlayerKillHierarchy(ServerPlayer player, DamageSource source) {
        if (player.dead) return;
        try {
            if (source.getEntity() instanceof LivingEntity attacker) {
                player.setLastHurtByMob(attacker);
                if (attacker instanceof Player p) {
                    player.setLastHurtByPlayer(p);
                }
            }
            player.getCombatTracker().recordDamage(source, 1000.0F);
            player.die(source);
        } catch (Throwable ignored) {
        }
        if (player.dead) return;
        try (var ignored = DecayDamageUtil.bypassScope(player)) {
            player.setHealth(0.0F);
            try {
                player.getEntityData().set(LivingEntityAccessor.getDataHealthId(), 0.0F);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            if (player.connection != null) {
                player.connection.send(new ClientboundSetHealthPacket(
                        0.0F,
                        player.getFoodData().getFoodLevel(),
                        player.getFoodData().getSaturationLevel()
                ));
            }
            dropAllForce(player);
            player.die(source);
        } catch (Throwable ignored) {
        }
        if (player.dead) return;
        try {
            if (player.server != null && player.isAlive()) {
                player.server.getPlayerList().respawn(player, false);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void breakBrain(LivingEntity entity) {
        if (entity instanceof Player) return;
        try {
            entity.getBrain().clearMemories();
            if (entity instanceof Mob mob) {
                breakGoalSelector(mob.goalSelector);
                breakGoalSelector(mob.targetSelector);
                mob.setTarget(null);
            }
        } catch (Throwable ignored) {
        }
    }

    public static void breakGoalSelector(GoalSelector goalSelector) {
        try {
            goalSelector.removeAllGoals(goal -> true);
            goalSelector.addGoal(0, new Goal() {
                @Override
                public boolean canUse() {
                    return false;
                }
            });
        } catch (Throwable ignored) {
        }
    }

    public static void breakControllers(LivingEntity entity) {
        if (entity == null) return;
        try {
            Class<?> clazz = entity.getClass();
            while (clazz != null && clazz != Entity.class && clazz != Object.class) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (!Modifier.isStatic(field.getModifiers()) &&
                            !field.getType().isPrimitive() &&
                            !field.getType().getName().startsWith("net.minecraft.") &&
                            !field.getType().getName().startsWith("java.")) {
                        field.setAccessible(true);
                        Object controller = field.get(entity);
                        if (controller != null) {
                            neutralizeController(controller, entity.level());
                            field.set(entity, null);
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void neutralizeController(Object controller, Level level) {
        if (controller == null) return;
        try {
            MinecraftServer server = level instanceof ServerLevel sl ? sl.getServer() : null;
            purgeBossBarsFromObjectFields(controller, server);
            if (controller instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Throwable ignored) {
                }
            }
            Class<?> ctrlClass = controller.getClass();
            while (ctrlClass != null && ctrlClass != Object.class) {
                for (Field f : ctrlClass.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) continue;
                    f.setAccessible(true);
                    Class<?> type = f.getType();
                    Object val = f.get(controller);
                    if (val == null) continue;
                    if (val instanceof Vec3) {
                        f.set(controller, new Vec3(0.0, -999999.0, 0.0));
                    } else if (val instanceof Collection<?> coll) {
                        try {
                            coll.clear();
                        } catch (Throwable ignored) {
                        }
                    } else if (val instanceof Map<?, ?> map) {
                        try {
                            map.clear();
                        } catch (Throwable ignored) {
                        }
                    } else if (type == int.class) {
                        f.setInt(controller, 0);
                    } else if (type == float.class) {
                        f.setFloat(controller, 0.0F);
                    } else if (type == double.class) {
                        f.setDouble(controller, 0.0);
                    } else if (Entity.class.isAssignableFrom(type)) {
                        f.set(controller, null);
                    }
                }
                ctrlClass = ctrlClass.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
    }

    public static void purgeFromExternalLists(LivingEntity entity) {
        if (entity == null) return;
        Class<?> clazz = entity.getClass();
        while (clazz != null && clazz != Entity.class && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers()) &&
                        !f.getType().isPrimitive() &&
                        !f.getType().getName().startsWith("net.minecraft.") &&
                        !f.getType().getName().startsWith("java.")) {
                    try {
                        f.setAccessible(true);
                        Object controller = f.get(entity);
                        if (controller != null) {
                            forceWipeArrayList(controller);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    private static void forceWipeArrayList(Object listObj) {
        if (listObj == null) return;
        Class<?> current = listObj.getClass();
        while (current != null && current != Object.class) {
            try {
                Field sizeField = current.getDeclaredField("size");
                sizeField.setAccessible(true);
                sizeField.setInt(listObj, 0);
            } catch (Throwable ignored) {
            }
            try {
                Field dataField = current.getDeclaredField("elementData");
                dataField.setAccessible(true);
                Object[] data = (Object[]) dataField.get(listObj);
                if (data != null) {
                    Arrays.fill(data, null);
                }
            } catch (Throwable ignored) {
            }
            current = current.getSuperclass();
        }
    }

    public static void dropAllForce(LivingEntity livingEntity) {
        if (livingEntity == null || livingEntity.level().isClientSide()) return;
        if (livingEntity instanceof Player player) {
            player.getInventory().dropAll();
            player.containerMenu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
        }
        List<Pair<EquipmentSlot, ItemStack>> emptySlotsList = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack itemStack = livingEntity.getItemBySlot(slot);
            if (itemStack != null && !itemStack.isEmpty()) {
                ItemStack stack = itemStack.copyAndClear();
                if (livingEntity.level() instanceof ServerLevel) {
                    livingEntity.spawnAtLocation(stack, 0.2F);
                }
                livingEntity.setItemSlot(slot, ItemStack.EMPTY);
                emptySlotsList.add(Pair.of(slot, ItemStack.EMPTY));
            }
        }
        if (!emptySlotsList.isEmpty() && livingEntity.level() instanceof ServerLevel serverLevel) {
            ClientboundSetEquipmentPacket equipPacket =
                    new ClientboundSetEquipmentPacket(livingEntity.getId(), emptySlotsList);
            serverLevel.getChunkSource().chunkMap.broadcast(livingEntity, equipPacket);
        }
    }

    public static void neutralizeEntityFields(LivingEntity entity) {
        if (entity == null || entity instanceof Player) return;
        try {
            Class<?> clazz = entity.getClass();
            while (clazz != null && clazz != LivingEntity.class && clazz != Entity.class) {
                for (Field f : clazz.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) continue;
                    f.setAccessible(true);
                    if (f.getType() == Vec3.class) {
                        f.set(entity, null);
                    } else if (Collection.class.isAssignableFrom(f.getType())) {
                        Object val = f.get(entity);
                        if (val instanceof Collection<?> col) {
                            try {
                                col.clear();
                            } catch (Throwable ignored) {
                            }
                        }
                    } else if (f.getType() == int.class) {
                        f.setInt(entity, 0);
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    public static void removeFromMemory(Entity victim) {
        if (victim == null) return;
        Level level = victim.level();
        if (level instanceof ServerLevel serverLevel) {

            ClientboundRemoveEntitiesPacket removePacket = new ClientboundRemoveEntitiesPacket(victim.getId());
            serverLevel.getChunkSource().chunkMap.broadcast(victim, removePacket);
            serverLevel.getChunkSource().chunkMap.removeEntity(victim);

            try {
                victim.levelCallback.onRemove(Entity.RemovalReason.KILLED);
            } catch (Throwable ignored) {}

            try {
                PersistentEntitySectionManager<Entity> manager = serverLevel.entityManager;
                if (manager != null) {

                    if (manager.visibleEntityStorage != null) {
                        manager.visibleEntityStorage.remove(victim);
                    }

                    EntitySectionStorage<Entity> sectionStorage = manager.sectionStorage;
                    if (sectionStorage != null && sectionStorage.sections != null) {
                        sectionStorage.sections.values().forEach(section -> {
                            if (section != null) {
                                try {

                                    Method removeMethod = section.getClass().getDeclaredMethod("remove", Object.class);
                                    removeMethod.setAccessible(true);
                                    removeMethod.invoke(section, victim);
                                } catch (Throwable ignored2) {}
                            }
                        });
                    }

                    if (manager.knownUuids != null) {
                        manager.knownUuids.remove(victim.getUUID());
                    }
                }
            } catch (Throwable ignored) {}

            serverLevel.entityTickList.remove(victim);
            serverLevel.getChunkSource().removeEntity(victim);
        }
    }
    private static Field getField(Class<?> clazz, String srgName, String mcpName) {
        try {
            return clazz.getDeclaredField(srgName);
        } catch (NoSuchFieldException e) {
            try {
                return clazz.getDeclaredField(mcpName);
            } catch (NoSuchFieldException ignored) {
                return null;
            }
        }
    }

    private static Field getFieldByType(Class<?> clazz, Class<?> type) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            for (Field f : current.getDeclaredFields()) {
                if (type.isAssignableFrom(f.getType())) {
                    return f;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    public static Set<UUID> extractAllLinkedUuids(Entity entity) {
        Set<UUID> uuids = new HashSet<>();
        if (entity == null) return uuids;
        UUID coreUuid = entity.getUUID();
        uuids.add(coreUuid);
        Set<UUID> playerUuids = new HashSet<>();
        if (entity.level() instanceof ServerLevel sl) {
            for (ServerPlayer sp : sl.getServer().getPlayerList().getPlayers()) {
                playerUuids.add(sp.getUUID());
            }
        }
        CompoundTag tag = entity.getPersistentData();
        if (tag != null) {
            for (String key : tag.getAllKeys()) {
                Tag val = tag.get(key);
                if (val instanceof IntArrayTag iat && iat.getAsIntArray().length == 4) {
                    try {
                        UUID u = NbtUtils.loadUUID(iat);
                        if (!playerUuids.contains(u)) uuids.add(u);
                    } catch (Throwable ignored) {
                    }
                } else if (tag.hasUUID(key)) {
                    UUID u = tag.getUUID(key);
                    if (!playerUuids.contains(u)) uuids.add(u);
                }
            }
        }
        Class<?> clazz = entity.getClass();
        while (clazz != null && clazz != Entity.class && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(entity);
                    if (val instanceof UUID u && !playerUuids.contains(u)) uuids.add(u);
                    if (val instanceof Optional<?> opt && opt.isPresent() && opt.get() instanceof UUID u && !playerUuids.contains(u)) {
                        uuids.add(u);
                    }
                } catch (Throwable ignored) {
                }
            }
            clazz = clazz.getSuperclass();
        }
        uuids.removeAll(playerUuids);
        return uuids;
    }

    public static void purgeSingleBossEvent(Object val, MinecraftServer server) {
        if (val instanceof ServerBossEvent serverBossEvent) {
            try {
                UUID bossBarId = serverBossEvent.getId();
                serverBossEvent.setVisible(false);
                serverBossEvent.removeAllPlayers();
                ClientboundBossEventPacket removePacket = ClientboundBossEventPacket.createRemovePacket(bossBarId);
                if (server != null) {
                    server.getPlayerList().broadcastAll(removePacket);
                }
            } catch (Throwable ignored) {
            }
        } else if (val instanceof BossEvent bossEvent) {
            try {
                bossEvent.setProgress(0.0F);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void purgeBossBarsFromObjectFields(Object obj, MinecraftServer server) {
        if (obj == null) return;
        Class<?> clazz = obj.getClass();
        while (clazz != null && clazz != Object.class) {
            for (Field f : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                try {
                    if (BossEvent.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Object val = f.get(obj);
                        if (val != null) {
                            purgeSingleBossEvent(val, server);
                            f.set(obj, null);
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            clazz = clazz.getSuperclass();
        }
    }
}