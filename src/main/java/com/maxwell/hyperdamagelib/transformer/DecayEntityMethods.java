package com.maxwell.hyperdamagelib.transformer;

import com.maxwell.hyperdamagelib.mixin.accessor.EntityAccessor;
import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.maxwell.hyperdamagelib.util.DecayDamageUtil;
import com.maxwell.hyperdamagelib.util.DecayForceKillHelper;
import com.maxwell.hyperdamagelib.util.InvincibleHelper;
import com.maxwell.hyperdamagelib.util.PurgedEntitiesSavedData;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class DecayEntityMethods {
    private DecayEntityMethods() {
    }

    public static boolean isP(Object obj) {
        return obj instanceof Entity entity && InvincibleHelper.isInvincible(entity);
    }

    public static boolean hdl$shouldCancelRemovePlayer(net.minecraft.server.level.DistanceManager distanceManager, net.minecraft.core.SectionPos sectionPos) {
        if (distanceManager == null || sectionPos == null) return true;
        long chunkKey = sectionPos.chunk().toLong();
        return distanceManager.playersPerChunk.get(chunkKey) == null;
    }

    public static boolean hdl$returnFalse(Object obj) {
        return false;
    }

    public static float hdl$getImmortalHealth(Object obj) {
        if (obj instanceof LivingEntity living) {
            float lockedHp = InvincibleHelper.getInvincibleHealth(living);
            return lockedHp > 0.0F ? lockedHp : 20.0F;
        }
        return 20.0F;
    }

    @SuppressWarnings("unchecked")
    public static <V> Int2ObjectMap<V> hdl$wrapEntityMap(Int2ObjectMap<V> original) {
        return new ProtectedEntityMap<>(original);
    }

    public static void hdl$forceTickInvulnerable(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide()) return;
        try {
            EntityAccessor entAcc = (EntityAccessor) entity;
            int invTime = entAcc.getInvulnerableTime();
            if (invTime > 0) {
                entAcc.setInvulnerableTime(invTime - 1);
            }
        } catch (Throwable ignored) {
        }
        try {
            if (entity.hurtTime > 0) {
                entity.hurtTime--;
            }
        } catch (Throwable ignored) {
        }
    }

    public static float hdl$hookSetHealth(Object obj, float pHealth) {
        if (obj instanceof LivingEntity entity) {
            if (isP(entity)) {
                float lockedHp = InvincibleHelper.getInvincibleHealth(entity);
                try {
                    entity.getEntityData().set(LivingEntityAccessor.getDataHealthId(), lockedHp);
                    if (entity instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
                        serverPlayer.connection.send(new ClientboundSetHealthPacket(
                                lockedHp,
                                serverPlayer.getFoodData().getFoodLevel(),
                                serverPlayer.getFoodData().getSaturationLevel()
                        ));
                    }
                } catch (Throwable ignored) {
                }
                return lockedHp;
            }
            if (DecayDamageUtil.isBypassDecay(entity)) {
                float clampedHp = Math.max(0.0F, pHealth);
                try {
                    entity.getEntityData().set(LivingEntityAccessor.getDataHealthId(), clampedHp);
                    if (entity instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
                        serverPlayer.connection.send(new ClientboundSetHealthPacket(
                                clampedHp,
                                serverPlayer.getFoodData().getFoodLevel(),
                                serverPlayer.getFoodData().getSaturationLevel()
                        ));
                    }
                } catch (Throwable ignored) {
                }
                return clampedHp;
            }
        }
        return pHealth;
    }

    public static float hdl$getHealth(Object obj) {
        if (obj instanceof LivingEntity entity) {
            if (isP(entity)) {
                return getRawHp(entity);
            }
            if (DecayDamageUtil.isForceDamage(entity)) {
                return -Float.MAX_VALUE;
            }
            return getRawHp(entity);
        }
        return 20.0F;
    }

    public static boolean hdl$isDeadOrDying(Object obj) {
        if (obj instanceof LivingEntity entity) {
            if (isP(entity)) {
                return false;
            }
            if (DecayDamageUtil.isForceDamage(entity)) {
                return true;
            }
            return getRawHp(entity) <= 0.0F || entity.dead;
        }
        return false;
    }

    public static boolean hdl$isAlive(Object obj) {
        if (obj instanceof LivingEntity living) {
            if (isP(living)) {
                return !InvincibleHelper.isRemoveBypass(living);
            }
            if (DecayDamageUtil.isForceDamage(living)) {
                return false;
            }
            return living.getRemovalReason() == null && getRawHp(living) > 0.0F;
        }
        if (obj instanceof Entity entity) {
            if (isP(entity)) {
                return !InvincibleHelper.isRemoveBypass(entity);
            }
            if (DecayDamageUtil.isForceDamage(entity)) {
                return false;
            }
            return entity.getRemovalReason() == null;
        }
        if (obj instanceof Thread thread) {
            return thread.isAlive();
        }
        return true;
    }

    public static boolean hdl$isRemoved(Object obj) {
        if (obj instanceof Entity entity) {
            if (isP(entity)) {
                return InvincibleHelper.isRemoveBypass(entity);
            }
            if (DecayDamageUtil.isForceDamage(entity)) {
                return true;
            }
            return entity.getRemovalReason() != null;
        }
        if (obj instanceof BlockEntity blockEntity) {
            return blockEntity.isRemoved();
        }
        return false;
    }

    public static Entity.RemovalReason hdl$getRemovalReason(Object obj) {
        if (obj instanceof Entity entity) {
            if (isP(entity) && !InvincibleHelper.isRemoveBypass(entity)) {
                return null;
            }
            return entity.getRemovalReason();
        }
        return null;
    }

    public static boolean hdl$shouldCancelRender(Object entityObj) {
        if (entityObj instanceof Entity entity) {
            if (DecayDamageUtil.isForceDamage(entity) || entity.isRemoved() || entity.getRemovalReason() != null) {
                return true;
            }
            try {
                for (Field f : entity.getClass().getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) && !f.getType().isPrimitive() &&
                            !f.getType().getName().startsWith("net.minecraft.")) {
                        f.setAccessible(true);
                        Object ctrl = f.get(entity);
                        if (ctrl != null && isControllerSilenced(ctrl)) {
                            return true;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    public static boolean isControllerSilenced(Object controller) {
        if (controller == null) return true;
        try {
            Class<?> clazz = controller.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field f : clazz.getDeclaredFields()) {
                    if (!Modifier.isStatic(f.getModifiers()) && f.getType() == net.minecraft.world.phys.Vec3.class) {
                        f.setAccessible(true);
                        Object val = f.get(controller);
                        if (val instanceof net.minecraft.world.phys.Vec3 vec && vec.y < -1000.0) {
                            return true;
                        }
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    public static boolean hdl$exists(Object obj) {
        if (obj == null) return false;
        if (obj instanceof Entity entity) {
            return !hdl$isRemoved(entity) && hdl$isAlive(entity);
        }
        return !isControllerDeadOrPurged(obj);
    }

    public static boolean isControllerDeadOrPurged(Object obj) {
        if (obj == null) return true;
        if (isControllerSilenced(obj)) {
            return true;
        }
        try {
            UUID ctrlUuid = extractControllerUuid(obj);
            if (ctrlUuid != null) {
                net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    PurgedEntitiesSavedData data = PurgedEntitiesSavedData.get(server.overworld());
                    if (data != null && data.isPurged(ctrlUuid)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
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

    public static void hdl$fixAlreadyTrackedEntity(ChunkMap chunkMap, Entity entity) {
        if (chunkMap == null || entity == null) return;
        try {
            chunkMap.entityMap.remove(entity.getId());
        } catch (Throwable ignored) {
        }
    }

    public static boolean hdl$shouldCancelTick(Object obj) {
        if (obj == null) return true;
        if (obj instanceof Entity entity) {
            if (entity.level() instanceof ServerLevel serverLevel) {
                PurgedEntitiesSavedData data = PurgedEntitiesSavedData.get(serverLevel);
                if (data != null && data.isPurged(entity)) {
                    DecayForceKillHelper.purgeBossBars(entity, serverLevel);
                    return true;
                }
            }
            return DecayDamageUtil.isForceDamage(entity) || entity.isRemoved() || entity.getRemovalReason() != null;
        }
        if (isControllerSilenced(obj)) {
            try {
                net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    DecayForceKillHelper.purgeBossBars(obj, server.overworld());
                }
            } catch (Throwable ignored) {
            }
            return true;
        }
        return false;
    }

    public static boolean hdl$shouldRejectEntityAdd(Object entityAccessObj) {
        if (entityAccessObj instanceof Entity entity) {
            if (DecayDamageUtil.isForceDamage(entity)) {
                return true;
            }
            if (entity.level() instanceof ServerLevel serverLevel) {
                try {
                    PurgedEntitiesSavedData data = PurgedEntitiesSavedData.get(serverLevel);
                    if (data != null && data.isPurged(entity)) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return false;
    }

    public static boolean hdl$shouldHideFromSpatialQuery(Entity entity) {
        if (entity == null) return false;
        if (isP(entity)) {
            return true;
        }
        return false;
    }

    public static <T> Predicate<T> hdl$wrapPredicate(Predicate<T> original) {
        return (target) -> {
            if (target instanceof Entity entity && isP(entity)) {
                return false;
            }
            return original == null || original.test(target);
        };
    }

    public static <T> Consumer<T> hdl$wrapConsumer(Consumer<T> original) {
        return (target) -> {
            if (target instanceof Entity entity && isP(entity)) {
                return;
            }
            if (original != null) {
                original.accept(target);
            }
        };
    }

    public static float sanitizeHookHealth(Object inst, float val, Object ent, Object p) {
        return (ent instanceof LivingEntity le) ? hdl$getHealth(le) : val;
    }

    public static boolean sanitizeHookDeadOrDying(Object inst, boolean val, Object ent, Object p) {
        return (ent instanceof LivingEntity le) ? hdl$isDeadOrDying(le) : val;
    }

    public static boolean sanitizeHookAlive(Object inst, boolean val, Object ent, Object p) {
        return (ent instanceof Entity e) ? hdl$isAlive(e) : val;
    }

    public static boolean sanitizeHookRemoved(Object inst, boolean val, Object ent, Object p) {
        return (ent instanceof Entity e) ? hdl$isRemoved(e) : val;
    }

    public static boolean shouldForceAttackable(Object obj) {
        if (obj instanceof Entity entity) {
            if (isP(entity)) {
                return false;
            }
            return InvincibleHelper.shouldForceAttackable(entity);
        }
        return false;
    }

    private static float getRawHp(LivingEntity e) {
        try {
            Float hp = e.getEntityData().get(LivingEntityAccessor.getDataHealthId());
            if (hp != null && !Float.isNaN(hp)) {
                return hp;
            }
        } catch (Throwable ignored) {
        }
        try {
            return e.deathTime > 0 ? 0.0F : e.getHealth();
        } catch (Throwable ignored) {
            return 0.0F;
        }
    }

    public static boolean hdl$handleUltraBypassHurt(Entity entity, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (entity == null || source == null || amount <= 0.0F) {
            return false;
        }
        if (isP(entity)) {
            return true;
        }
        if (!(entity instanceof LivingEntity livingTarget) || entity.level().isClientSide()) {
            return false;
        }
        if (DecayDamageUtil.isBypassDecay(livingTarget)) {
            return false;
        }
        if (source.is(DecayDamageUtil.ULTRA_BYPASS_DAMAGE) ||
                source.is(com.maxwell.hyperdamagelib.init.ModDamageTypes.EROSION) ||
                source.is(com.maxwell.hyperdamagelib.init.ModDamageTypes.PENETRATE)) {
            DecayDamageUtil.applyCustomDamage(livingTarget, source, amount);
            return true;
        }
        return false;
    }

    private static class ProtectedEntityMap<V> implements Int2ObjectMap<V> {
        private final Int2ObjectMap<V> delegate;

        public ProtectedEntityMap(Int2ObjectMap<V> delegate) {
            this.delegate = delegate;
        }

        private boolean shouldHide(Object val) {
            if (!isP(val)) return false;
            if (val instanceof Entity entity && entity.level().isClientSide()) {
                return false;
            }
            return true;
        }

        @Override
        public ObjectCollection<V> values() {
            ObjectCollection<V> originalValues = this.delegate.values();
            List<V> filtered = originalValues.stream()
                    .filter(e -> !shouldHide(e))
                    .collect(Collectors.toList());
            return new ObjectArrayList<>(filtered);
        }

        @Override
        public V get(int key) {
            V val = this.delegate.get(key);
            return shouldHide(val) ? null : val;
        }

        @Override
        public V get(Object key) {
            V val = this.delegate.get(key);
            return shouldHide(val) ? null : val;
        }

        @Override
        public int size() {
            return (int) this.delegate.values().stream().filter(e -> !shouldHide(e)).count();
        }

        @Override
        public boolean isEmpty() {
            return size() == 0;
        }

        @Override
        public boolean containsValue(Object value) {
            return shouldHide(value) ? false : this.delegate.containsValue(value);
        }

        @Override
        public boolean containsKey(int key) {
            return get(key) != null;
        }

        @Override
        public V put(int key, V value) {
            return this.delegate.put(key, value);
        }

        @Override
        public V remove(int key) {
            return this.delegate.remove(key);
        }

        @Override
        public void putAll(Map<? extends Integer, ? extends V> m) {
            this.delegate.putAll(m);
        }

        @Override
        public void defaultReturnValue(V rv) {
            this.delegate.defaultReturnValue(rv);
        }

        @Override
        public V defaultReturnValue() {
            return this.delegate.defaultReturnValue();
        }

        @Override
        public ObjectSet<Entry<V>> int2ObjectEntrySet() {
            return this.delegate.int2ObjectEntrySet();
        }

        @Override
        public it.unimi.dsi.fastutil.ints.IntSet keySet() {
            return this.delegate.keySet();
        }

        @Override
        public void clear() {
            this.delegate.clear();
        }
    }
}