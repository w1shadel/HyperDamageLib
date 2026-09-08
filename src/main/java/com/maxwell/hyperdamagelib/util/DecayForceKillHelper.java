package com.maxwell.hyperdamagelib.util;

import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class DecayForceKillHelper {
    public static void decayForceKill(LivingEntity entity) {
        if (entity.level().isClientSide()) return;

        DecayDamageUtil.markPermanentlyKilled(entity);

        try (var ignored1 = DecayDamageUtil.forceKillScope(entity)) {
            preventEntitySaving(entity);
            if (entity.level() instanceof ServerLevel serverLevel) {
                PurgedEntitiesSavedData.get(serverLevel).markPurged(entity);
            }

            purgeBossBars(entity, entity.level());
            breakBrain(entity);
            neutralizeEntityFields(entity);

            try (var ignored2 = DecayDamageUtil.bypassScope(entity)) {
                entity.setHealth(0.0F);
                entity.getEntityData().set(LivingEntityAccessor.getDataHealthId(), 0.0F);
            }

            DamageSource erosion = DecayDamageUtil.getErosionSource(entity.level(), entity);
            entity.die(erosion);
            dropAllForce(entity);

            if (!(entity instanceof Player)) {
                InvincibleHelper.setRemoveBypass(entity, true);
                entity.remove(Entity.RemovalReason.KILLED);
                entity.discard();
                removeFromMemory(entity);
                breakControllers(entity);
                purgeEntityFromStaticCaches(entity);
                purgeFromExternalLists(entity);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void preventEntitySaving(Entity entity) {
        if (entity == null) return;
        try {
            entity.setRemoved(Entity.RemovalReason.KILLED);
            net.minecraft.nbt.CompoundTag persistentData = entity.getPersistentData();
            if (persistentData != null) {
                for (String key : persistentData.getAllKeys().toArray(new String[0])) {
                    persistentData.remove(key);
                }
            }
            entity.getTags().clear();

        } catch (Throwable ignored) {
        }
    }

    public static void breakBrain(LivingEntity entity) {
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

    public static void purgeFromExternalLists(LivingEntity entity) {
        if (entity == null) return;
        purgeObjectFromClassHierarchy(entity.getClass(), entity);
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
                            purgeObjectFromClassHierarchy(controller.getClass(), controller);
                            purgeFromReferencedClasses(controller.getClass(), controller);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    public static void purgeBossBars(Object target, Level level) {
        if (target == null) return;
        try {
            Class<?> clazz = target.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field f : clazz.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers())) continue;
                    if (net.minecraft.world.BossEvent.class.isAssignableFrom(f.getType())) {
                        try {
                            f.setAccessible(true);
                            Object val = f.get(target);
                            if (val instanceof net.minecraft.server.level.ServerBossEvent serverBossEvent) {
                                java.util.UUID bossId = serverBossEvent.getId();
                                serverBossEvent.setVisible(false);
                                serverBossEvent.removeAllPlayers();
                                net.minecraft.network.protocol.game.ClientboundBossEventPacket removePacket =
                                        net.minecraft.network.protocol.game.ClientboundBossEventPacket.createRemovePacket(bossId);
                                if (level instanceof ServerLevel serverLevel) {
                                    serverLevel.getServer().getPlayerList().broadcastAll(removePacket);
                                }
                            } else if (val instanceof net.minecraft.world.BossEvent bossEvent) {
                                bossEvent.setProgress(0.0F);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
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
                            if (entity.level() instanceof ServerLevel serverLevel) {
                                UUID ctrlUuid = extractControllerUuid(controller);
                                if (ctrlUuid != null) {
                                    PurgedEntitiesSavedData.get(serverLevel).markPurged(ctrlUuid);
                                }
                            }
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

    private static UUID extractControllerUuid(Object controller) {
        try {
            Class<?> clazz = controller.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field f : clazz.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) && f.getType() == UUID.class) {
                        f.setAccessible(true);
                        Object val = f.get(controller);
                        if (val instanceof UUID uuid) {
                            return uuid;
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void purgeFromReferencedClasses(Class<?> targetClass, Object objectToRemove) {
        if (targetClass == null || objectToRemove == null) return;
        Package pkg = targetClass.getPackage();
        if (pkg == null) return;
        String pkgName = pkg.getName();
        Set<Class<?>> classesToScan = new HashSet<>();
        classesToScan.add(targetClass);
        for (Field f : targetClass.getDeclaredFields()) {
            addClassIfSamePackage(classesToScan, f.getType(), pkgName);
        }
        for (Method m : targetClass.getDeclaredMethods()) {
            addClassIfSamePackage(classesToScan, m.getReturnType(), pkgName);
            for (Class<?> pType : m.getParameterTypes()) {
                addClassIfSamePackage(classesToScan, pType, pkgName);
            }
        }
        for (Class<?> declared : targetClass.getDeclaredClasses()) {
            addClassIfSamePackage(classesToScan, declared, pkgName);
        }
        for (Class<?> c : classesToScan) {
            purgeStaticCollectionsIn(c, objectToRemove);
        }
    }

    private static void addClassIfSamePackage(Set<Class<?>> set, Class<?> type, String pkgName) {
        if (type != null && type.getName().startsWith(pkgName)) {
            set.add(type);
        }
    }

    private static void purgeObjectFromClassHierarchy(Class<?> targetClass, Object objectToRemove) {
        if (targetClass == null || objectToRemove == null) return;
        try {
            Class<?> current = targetClass;
            while (current != null && current != Object.class) {
                purgeStaticCollectionsIn(current, objectToRemove);
                for (Class<?> declared : current.getDeclaredClasses()) {
                    purgeStaticCollectionsIn(declared, objectToRemove);
                }
                current = current.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void purgeStaticCollectionsIn(Class<?> clazz, Object objectToRemove) {
        for (Field f : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && Collection.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    Object colObj = f.get(null);
                    if (colObj != null) {
                        forceWipeArrayList(colObj);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void forceWipeArrayList(Object listObj) {
        Class<?> current = listObj.getClass();
        while (current != null && current != Object.class) {
            try {
                Field sizeField = current.getDeclaredField("size");
                sizeField.setAccessible(true);
                sizeField.setInt(listObj, 0);
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
            }
            try {
                Field dataField = current.getDeclaredField("elementData");
                dataField.setAccessible(true);
                Object[] data = (Object[]) dataField.get(listObj);
                if (data != null) {
                    java.util.Arrays.fill(data, null);
                }
            } catch (NoSuchFieldException | IllegalAccessException ignored) {
            }
            try {
                for (Field f : current.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) && Collection.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        Object inner = f.get(listObj);
                        if (inner != null && inner != listObj) {
                            forceWipeArrayList(inner);
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
            current = current.getSuperclass();
        }
    }

    private static void neutralizeController(Object controller, Level level) {
        if (controller == null) return;
        try {
            purgeBossBars(controller, level);
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
                    if (val instanceof net.minecraft.world.phys.Vec3) {
                        f.set(controller, new net.minecraft.world.phys.Vec3(0.0, -999999.0, 0.0));
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

    public static void purgeEntityFromStaticCaches(Entity entity) {
        if (entity == null) return;
        try {
            Class<?> clazz = entity.getClass();
            while (clazz != null && clazz != Entity.class && clazz != Object.class) {
                purgeMapsInClass(clazz, entity);
                for (Class<?> declaredClass : clazz.getDeclaredClasses()) {
                    purgeMapsInClass(declaredClass, entity);
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void purgeMapsInClass(Class<?> clazz, Entity entity) {
        for (Field f : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && Map.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    Map<?, ?> map = (Map<?, ?>) f.get(null);
                    if (map != null) {
                        map.remove(entity);
                        map.remove(entity.getUUID());
                        map.remove(entity.getId());
                    }
                } catch (Throwable ignored) {
                }
            }
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

                clearStackAndDrop(livingEntity, itemStack);

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

    public static void clearStackAndDrop(Entity entity, ItemStack itemStack) {
        if (itemStack != null && !itemStack.isEmpty()) {
            ItemStack stack = itemStack.copyAndClear();
            if (entity.level() instanceof ServerLevel) {
                entity.spawnAtLocation(stack, 0.2F);
            }
        }
    }

    public static void neutralizeEntityFields(LivingEntity entity) {
        if (entity == null) return;
        try {
            Class<?> clazz = entity.getClass();
            while (clazz != null && clazz != LivingEntity.class && clazz != Entity.class) {
                for (Field f : clazz.getDeclaredFields()) {
                    if (Modifier.isStatic(f.getModifiers()) || Modifier.isFinal(f.getModifiers())) continue;
                    f.setAccessible(true);

                    if (f.getType() == net.minecraft.world.phys.Vec3.class) {
                        f.set(entity, null);
                    }

                    else if (Collection.class.isAssignableFrom(f.getType())) {
                        Object val = f.get(entity);
                        if (val instanceof Collection<?> col) {
                            try {
                                col.clear();
                            } catch (Throwable ignored) {
                            }
                        }
                    }

                    else if (f.getType() == int.class) {
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
            net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket removePacket =
                    new net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket(victim.getId());
            serverLevel.getChunkSource().chunkMap.broadcast(victim, removePacket);
            victim.levelCallback.onRemove(Entity.RemovalReason.KILLED);
            victim.levelCallback = EntityInLevelCallback.NULL;
            PersistentEntitySectionManager<Entity> manager = serverLevel.entityManager;
            EntitySectionStorage<Entity> sectionStorage = manager.sectionStorage;
            if (manager.isLoaded(victim.getUUID())) {
                long index = SectionPos.of(victim.blockPosition()).asLong();
                EntitySection<Entity> tSection = sectionStorage.getSection(index);
                if (Objects.nonNull(tSection)) {
                    EntitySection<Entity> newSection = new EntitySection<>(Entity.class, tSection.getStatus());
                    tSection.getEntities().filter(entity -> victim != entity).forEach(newSection::add);
                    sectionStorage.sections.replace(index, newSection);
                }
                manager.knownUuids.remove(victim.getUUID());
            }
            EntityLookup<Entity> entityLookup = manager.visibleEntityStorage;
            entityLookup.remove(victim);
            if (entityLookup.getEntity(victim.getId()) != null) {
                EntityLookup<Entity> newEntityLookup = new EntityLookup<>();
                for (Entity entity : entityLookup.getAllEntities()) {
                    if (entity != victim) newEntityLookup.add(entity);
                }
                manager.visibleEntityStorage = newEntityLookup;
                manager.entityGetter = new LevelEntityGetterAdapter<>(newEntityLookup, sectionStorage);
            }
            serverLevel.entityTickList.remove(victim);
            serverLevel.entityTickList.active.remove(victim.getId());
            serverLevel.entityTickList.passive.remove(victim.getId());
            if (serverLevel.entityTickList.iterated != null) {
                serverLevel.entityTickList.iterated.remove(victim.getId());
            }
            serverLevel.getChunkSource().removeEntity(victim);
        }
    }
}