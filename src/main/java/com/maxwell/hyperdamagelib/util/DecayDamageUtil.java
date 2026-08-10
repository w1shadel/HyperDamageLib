package com.maxwell.hyperdamagelib.util;

import com.maxwell.hyperdamagelib.init.ModDamageTypes;
import com.maxwell.hyperdamagelib.item.ErosionSwordItem;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

public final class DecayDamageUtil {
    public static final TagKey<DamageType> ULTRA_BYPASS_DAMAGE = TagKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation("hyperdamagelib", "ultra_bypass_damage")
    );
    public static final ThreadLocal<Boolean> FORCE_DAMAGE = ThreadLocal.withInitial(() -> false);
    public static final ThreadLocal<Boolean> BYPASS_DECAY = ThreadLocal.withInitial(() -> false);
    public static final ThreadLocal<Boolean> BYPASS_EFFECT = ThreadLocal.withInitial(() -> false);

    private DecayDamageUtil() {
    }

    public static void forceSetHealthVanillaRawDirect(SynchedEntityData entityData, EntityDataAccessor<?> accessor, Object value) {
        DecaySecurity.checkReflectionAccess();
        try {
            java.lang.reflect.Field itemsField = null;
            try {
                itemsField = entityData.getClass().getDeclaredField("itemsById");
            } catch (NoSuchFieldException e) {
                try {
                    itemsField = entityData.getClass().getDeclaredField("f_135345_");
                } catch (NoSuchFieldException ex) {
                    for (java.lang.reflect.Field f : entityData.getClass().getDeclaredFields()) {
                        if (java.util.Map.class.isAssignableFrom(f.getType())) {
                            itemsField = f;
                            break;
                        }
                    }
                }
            }
            if (itemsField != null) {
                itemsField.setAccessible(true);
                java.util.Map<?, ?> itemsMap = (java.util.Map<?, ?>) itemsField.get(entityData);
                if (itemsMap != null) {
                    for (Object itemObj : itemsMap.values()) {
                        if (itemObj == null) continue;
                        java.lang.reflect.Field accessorField = null;
                        java.lang.reflect.Field valueField = null;
                        java.lang.reflect.Field dirtyField = null;
                        for (java.lang.reflect.Field f : itemObj.getClass().getDeclaredFields()) {
                            if (f.getType().getName().contains("EntityDataAccessor")) {
                                accessorField = f;
                            } else if (f.getType() == Object.class) {
                                if (!java.lang.reflect.Modifier.isFinal(f.getModifiers())) {
                                    valueField = f;
                                }
                            } else if (f.getType() == boolean.class) {
                                dirtyField = f;
                            }
                        }
                        if (accessorField != null && valueField != null) {
                            accessorField.setAccessible(true);
                            valueField.setAccessible(true);
                            EntityDataAccessor<?> acc = (EntityDataAccessor<?>) accessorField.get(itemObj);
                            if (acc != null && acc.equals(accessor)) {
                                valueField.set(itemObj, value);
                                if (dirtyField != null) {
                                    dirtyField.setAccessible(true);
                                    dirtyField.setBoolean(itemObj, true);
                                }
                                java.lang.reflect.Field isDirtyField = null;
                                try {
                                    isDirtyField = entityData.getClass().getDeclaredField("isDirty");
                                } catch (NoSuchFieldException e) {
                                    try {
                                        isDirtyField = entityData.getClass().getDeclaredField("f_135348_");
                                    } catch (NoSuchFieldException ex) {
                                        for (java.lang.reflect.Field ef : entityData.getClass().getDeclaredFields()) {
                                            if (ef.getType() == boolean.class && ef.getName().equals("isDirty")) {
                                                isDirtyField = ef;
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (isDirtyField != null) {
                                    isDirtyField.setAccessible(true);
                                    isDirtyField.setBoolean(entityData, true);
                                }
                                java.lang.reflect.Field entityField = null;
                                try {
                                    entityField = entityData.getClass().getDeclaredField("entity");
                                } catch (NoSuchFieldException e) {
                                    try {
                                        entityField = entityData.getClass().getDeclaredField("f_135344_");
                                    } catch (NoSuchFieldException ex) {
                                        for (java.lang.reflect.Field ef : entityData.getClass().getDeclaredFields()) {
                                            if (Entity.class.isAssignableFrom(ef.getType())) {
                                                entityField = ef;
                                                break;
                                            }
                                        }
                                    }
                                }
                                if (entityField != null) {
                                    entityField.setAccessible(true);
                                    Entity entity = (Entity) entityField.get(entityData);
                                    if (entity != null) {
                                        entity.onSyncedDataUpdated(accessor);
                                    }
                                }
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            com.maxwell.hyperdamagelib.HDL.LOGGER.error("[HDL] Failed to direct set health via Map", t);
        }
    }

    public static boolean forceAddEffect(LivingEntity entity, MobEffectInstance effectInstance, @Nullable Entity source) {
        try {
            BYPASS_EFFECT.set(true);
            return entity.addEffect(effectInstance, source);
        } finally {
            BYPASS_EFFECT.remove();
        }
    }

    public static DamageSource getErosionSource(Level level, @Nullable Entity attacker, @Nullable String customDeathMessage) {
        DecaySecurity.checkReflectionAccess();
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ModDamageTypes.EROSION);
        return new DamageSource(holder, attacker) {
            @Override
            public Component getLocalizedDeathMessage(LivingEntity victim) {
                if (customDeathMessage != null && !customDeathMessage.isEmpty()) {
                    Component formatted = formatCustomMessage(customDeathMessage, victim, this.getEntity());
                    if (formatted != null) return formatted;
                }
                return super.getLocalizedDeathMessage(victim);
            }
        };
    }

    public static DamageSource getErosionSource(Level level, @Nullable Entity attacker) {
        return getErosionSource(level, attacker, null);
    }

    public static DamageSource getVoidShredSource(Level level, @Nullable Entity attacker, @Nullable String customDeathMessage) {
        DecaySecurity.checkReflectionAccess();
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ModDamageTypes.VOID_SHRED);
        return new DamageSource(holder, attacker) {
            @Override
            public Component getLocalizedDeathMessage(LivingEntity victim) {
                if (customDeathMessage != null && !customDeathMessage.isEmpty()) {
                    Component formatted = formatCustomMessage(customDeathMessage, victim, this.getEntity());
                    if (formatted != null) return formatted;
                }
                return super.getLocalizedDeathMessage(victim);
            }
        };
    }

    public static DamageSource getVoidShredSource(Level level, @Nullable Entity attacker) {
        return getVoidShredSource(level, attacker, null);
    }

    public static DamageSource getPenetrateSource(Level level, @Nullable Entity attacker, @Nullable String customDeathMessage) {
        DecaySecurity.checkReflectionAccess();
        Holder<DamageType> holder;
        try {
            holder = level.registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(ModDamageTypes.PENETRATE);
        } catch (Throwable t) {
            com.maxwell.hyperdamagelib.HDL.LOGGER.error("[HDL] Penetrate DamageType JSON not loaded! Falling back to GENERIC.", t);
            holder = level.registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.GENERIC);
        }
        final Holder<DamageType> finalHolder = holder;
        return new DamageSource(finalHolder, attacker) {
            @Override
            public Component getLocalizedDeathMessage(LivingEntity victim) {
                if (customDeathMessage != null && !customDeathMessage.isEmpty()) {
                    Component formatted = formatCustomMessage(customDeathMessage, victim, this.getEntity());
                    if (formatted != null) return formatted;
                }
                return super.getLocalizedDeathMessage(victim);
            }
        };
    }

    public static DamageSource getPenetrateSource(Level level, @Nullable Entity attacker) {
        return getPenetrateSource(level, attacker, null);
    }

    public static Component formatCustomMessage(String template, LivingEntity victim, @Nullable Entity attacker) {
        if (template == null || template.isEmpty()) return null;
        String victimName = victim.getDisplayName().getString();
        String attackerName = attacker != null ? attacker.getDisplayName().getString() : "";
        String formatted = template
                .replace("%victim%", victimName)
                .replace("%attacker%", attackerName);
        if (formatted.contains("%s")) {
            try {
                formatted = String.format(formatted, victimName, attackerName);
            } catch (Exception ignored) {
            }
        }
        return Component.literal(formatted);
    }

    public static boolean shouldApplyBypass(DamageSource source) {
        if (source.is(ULTRA_BYPASS_DAMAGE)) {
            return true;
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof LivingEntity livingAttacker) {
            ItemStack heldItem = livingAttacker.getMainHandItem();
            if (heldItem.getItem() instanceof ErosionSwordItem) {
                return true;
            }
        }
        return false;
    }
}