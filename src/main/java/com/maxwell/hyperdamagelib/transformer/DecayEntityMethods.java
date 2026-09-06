package com.maxwell.hyperdamagelib.transformer;

import com.maxwell.hyperdamagelib.HDL;
import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.maxwell.hyperdamagelib.util.DecayDamageUtil;
import com.maxwell.hyperdamagelib.util.InvincibleHelper;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class DecayEntityMethods {
    private DecayEntityMethods() {}

    public static boolean isP(Object obj) {
        return obj instanceof Entity entity && InvincibleHelper.isInvincible(entity);
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
                } catch (Throwable ignored) {}
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
                } catch (Throwable ignored) {}
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
            return getRawHp(entity) <= 0.0F;
        }
        return false;
    }

    public static boolean hdl$isAlive(Object obj) {
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
        } catch (Throwable ignored) {}
        try {
            float max = e.getMaxHealth();
            return max > 0.0F ? max : 20.0F;
        } catch (Throwable ignored) {
            return 20.0F;
        }
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

        @Override public int size() { return (int) this.delegate.values().stream().filter(e -> !shouldHide(e)).count(); }
        @Override public boolean isEmpty() { return size() == 0; }
        @Override public boolean containsValue(Object value) { return shouldHide(value) ? false : this.delegate.containsValue(value); }
        @Override public boolean containsKey(int key) { return get(key) != null; }
        @Override public V put(int key, V value) { return this.delegate.put(key, value); }
        @Override public V remove(int key) { return this.delegate.remove(key); }
        @Override public void putAll(Map<? extends Integer, ? extends V> m) { this.delegate.putAll(m); }
        @Override public void defaultReturnValue(V rv) { this.delegate.defaultReturnValue(rv); }
        @Override public V defaultReturnValue() { return this.delegate.defaultReturnValue(); }
        @Override public ObjectSet<Entry<V>> int2ObjectEntrySet() { return this.delegate.int2ObjectEntrySet(); }
        @Override public it.unimi.dsi.fastutil.ints.IntSet keySet() { return this.delegate.keySet(); }
        @Override public void clear() { this.delegate.clear(); }
    }
}