package com.maxwell.hyperdamagelib.transformer;

import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.maxwell.hyperdamagelib.mixin.accessor.SynchedEntityDataAccessor;
import com.maxwell.hyperdamagelib.util.DecayDamageUtil;
import com.maxwell.hyperdamagelib.util.IDecayEntity;
import com.maxwell.hyperdamagelib.util.InvincibleHelper;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class DecayEntityMethods {
    private DecayEntityMethods() {
    }

    public static boolean isP(Entity e) {
        if (e == null) return false;
        return InvincibleHelper.isInvincible(e) || (e instanceof IDecayEntity d && d.isSuperInvincible());
    }

    public static boolean isReallyAlive(Entity e) {
        if (e == null) return false;
        if (isP(e)) return !InvincibleHelper.isRemoveBypass(e);
        if (DecayDamageUtil.FORCE_DAMAGE.get()) return false;
        if (e instanceof LivingEntity le) {
            return getTrueHealth(le) > 0.0F && !isReallyRemoved(e);
        }
        return !isReallyRemoved(e);
    }

    public static boolean isReallyDeadOrDying(LivingEntity e) {
        if (e == null) return true;
        if (isP(e)) return false;
        if (DecayDamageUtil.FORCE_DAMAGE.get()) return true;
        return !isReallyAlive(e);
    }

    public static boolean shouldInterceptClientRemove(net.minecraft.world.level.Level level, int entityId) {
        if (level == null) return false;
        Entity entity = level.getEntity(entityId);
        return isP(entity) && !InvincibleHelper.isRemoveBypass(entity);
    }

    public static boolean isReallyRemoved(Entity e) {
        if (e == null) return true;
        if (isP(e)) {
            return InvincibleHelper.isRemoveBypass(e);
        }
        if (DecayDamageUtil.FORCE_DAMAGE.get()) return true;
        return e.getRemovalReason() != null;
    }

    public static float getTrueHealth(LivingEntity e) {
        if (e == null) return 0.0F;
        if (isP(e)) return (e instanceof IDecayEntity d) ? d.getInvincibleHealthValue() : getRawHp(e);
        if (DecayDamageUtil.FORCE_DAMAGE.get()) return -Float.MAX_VALUE;
        float max = Math.max(20.0f, e.getMaxHealth());
        float decay = (e instanceof IDecayEntity d) ? d.getDecayAmount() : 0.0f;
        return (decay >= max) ? -Float.MAX_VALUE : Math.max(0.0f, Math.min(getRawHp(e), max - decay));
    }

    public static boolean shouldReplaceHealthMethod(Entity e) {
        if (DecayDamageUtil.BYPASS_DECAY.get() || e == null) return false;
        if (isP(e)) return true;
        if (e instanceof LivingEntity le) {
            return getTrueHealth(le) > 0.0F;
        }
        return false;
    }

    public static float sanitizeHookHealth(Object inst, float val, Object ent, Object p) {
        return (ent instanceof LivingEntity le) ? getTrueHealth(le) : val;
    }

    public static boolean sanitizeHookAlive(Object inst, boolean val, Object ent, Object p) {
        return (ent instanceof Entity e) ? isReallyAlive(e) : val;
    }

    public static boolean sanitizeHookDeadOrDying(Object inst, boolean val, Object ent, Object p) {
        return (ent instanceof LivingEntity le) ? isReallyDeadOrDying(le) : val;
    }

    public static boolean sanitizeHookRemoved(Object inst, boolean val, Object ent, Object p) {
        return (ent instanceof Entity e) ? isReallyRemoved(e) : val;
    }

    public static boolean shouldInterceptTickDeath(LivingEntity e) {
        return isP(e) || getTrueHealth(e) > 0.0F;
    }

    public static int sanitizeDeathTimeWrite(int val, LivingEntity e) {
        return (isP(e) || getTrueHealth(e) > 0.0F) ? 0 : val;
    }

    public static boolean sanitizeDeadFlagWrite(boolean val, LivingEntity e) {
        return (isP(e) || getTrueHealth(e) > 0.0F) ? false : val;
    }

    public static float sanitizeStaticHealth(float val, LivingEntity e) {
        return getTrueHealth(e);
    }

    public static boolean sanitizeStaticDeadOrDying(boolean val, LivingEntity e) {
        return isReallyDeadOrDying(e);
    }

    public static boolean sanitizeStaticAlive(boolean val, Entity e) {
        return isReallyAlive(e);
    }

    public static void interceptKillCall(Entity e) {
        if (!isP(e)) e.kill();
    }

    public static void interceptDiscardCall(Entity e) {
        if (!isP(e)) e.discard();
    }

    public static boolean shouldInterceptDie(LivingEntity entity) {
        return isP(entity);
    }

    public static void forceStateSync(LivingEntity entity) {
        if (isP(entity)) {
            entity.deathTime = 0;
            entity.dead = false;
            if (entity.getHealth() <= 0.01f) {
                entity.setHealth(entity.getMaxHealth());
            }
        }
    }

    public static void interceptDataUpdate(SynchedEntityData data, EntityDataAccessor<?> accessor, Object value) {
        interceptDataUpdate(data, accessor, value, false);
    }

    public static boolean shouldInterceptSetPose(Entity e, net.minecraft.world.entity.Pose pose) {
        return isP(e) && pose == net.minecraft.world.entity.Pose.DYING;
    }

    public static void interceptDataUpdate(SynchedEntityData data, EntityDataAccessor<?> accessor, Object value, boolean force) {
        if (accessor.equals(LivingEntity.DATA_HEALTH_ID) && value instanceof Float f && f <= 0.0f) {
            Entity rawEntity = ((SynchedEntityDataAccessor) data).getEntity();
            if (rawEntity instanceof LivingEntity owner && isP(owner)) {
                data.set((EntityDataAccessor<Float>) accessor, owner.getMaxHealth(), force);
                return;
            }
        }
        data.set((EntityDataAccessor<Object>) accessor, value, force);
    }

    public static boolean shouldInterceptSetPos(Entity e, double x, double y, double z) {
        if (!isP(e)) return false;
        return Double.isNaN(x) || Double.isInfinite(x) || Math.abs(x) > 29999984.0D || Math.abs(z) > 29999984.0D || Math.abs(y) > 20000000.0D;
    }

    public static boolean shouldInterceptRemoval(Entity e, Entity.RemovalReason r) {
        if (e == null || InvincibleHelper.isRemoveBypass(e)) return false;
        if (r == Entity.RemovalReason.CHANGED_DIMENSION || r == Entity.RemovalReason.UNLOADED_WITH_PLAYER || r == Entity.RemovalReason.UNLOADED_TO_CHUNK) {
            return false;
        }
        return isP(e);
    }

    public static boolean shouldInterceptChunkMapRemove(Entity e) {
        return isP(e) && !InvincibleHelper.isRemoveBypass(e);
    }

    public static boolean shouldInterceptKill(Entity e) {
        return isP(e) && !InvincibleHelper.isRemoveBypass(e);
    }

    public static boolean shouldInterceptTickListRemove(Entity e) {
        return shouldInterceptKill(e);
    }

    public static boolean shouldInterceptLookupRemove(Object o) {
        return (o instanceof Entity e) && shouldInterceptKill(e);
    }

    private static float getRawHp(LivingEntity e) {
        try {
            return e.getEntityData().get(LivingEntityAccessor.getDataHealthId());
        } catch (Throwable t) {
            return e.getMaxHealth();
        }
    }

    public static float replaceGetHealth(LivingEntity e) {
        return getTrueHealth(e);
    }

    public static boolean replaceIsAlive(Entity e) {
        return isReallyAlive(e);
    }

    public static boolean replaceIsDeadOrDying(Entity e) {
        return (e instanceof LivingEntity le) && isReallyDeadOrDying(le);
    }

    public static boolean shouldReplaceIsPickable(Entity entity) {
        return entity instanceof IDecayEntity decay && decay.isIntangible();
    }

    public static boolean replaceIsPickable(Entity e) {
        return false;
    }

    public static boolean shouldReplaceIsAttackable(Entity entity) {
        return entity instanceof IDecayEntity decay && decay.isIntangible();
    }

    public static boolean replaceIsAttackable(Entity e) {
        return false;
    }

    public static boolean shouldReplaceCanBeHitByProjectile(Entity entity) {
        return entity instanceof IDecayEntity decay && decay.isIntangible();
    }

    public static boolean replaceCanBeHitByProjectile(Entity e) {
        return false;
    }
}