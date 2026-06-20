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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.Map;

public final class DecayDamageUtil {
    public static final TagKey<DamageType> ULTRA_BYPASS_DAMAGE = TagKey.create(
            Registries.DAMAGE_TYPE,
            new ResourceLocation("hyperdamagelib", "ultra_bypass_damage")
    );
    public static final ThreadLocal<Boolean> BYPASS_DECAY = ThreadLocal.withInitial(() -> false);

    private DecayDamageUtil() {
    }

    public static void forceSetHealthVanillaRawDirect(SynchedEntityData entityData, EntityDataAccessor<?> accessor, Object value) {
        try {
            for (Field field : entityData.getClass().getDeclaredFields()) {
                if (field.getType().getName().contains("Int2ObjectMap") || Map.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    Map<?, ?> itemsMap = (Map<?, ?>) field.get(entityData);
                    if (itemsMap != null) {
                        for (Object itemObj : itemsMap.values()) {
                            if (itemObj == null) continue;
                            Field accessorField = null;
                            Field valueField = null;
                            Field dirtyField = null;
                            for (Field f : itemObj.getClass().getDeclaredFields()) {
                                if (f.getType().getName().contains("EntityDataAccessor")) {
                                    accessorField = f;
                                } else if (f.getType() == Object.class) {
                                    valueField = f;
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
                                    Field entityField = null;
                                    for (Field ef : entityData.getClass().getDeclaredFields()) {
                                        if (Entity.class.isAssignableFrom(ef.getType())) {
                                            entityField = ef;
                                            break;
                                        }
                                    }
                                    if (entityField != null) {
                                        entityField.setAccessible(true);
                                        Entity entity = (Entity) entityField.get(entityData);
                                        if (entity != null) {
                                            entity.onSyncedDataUpdated(accessor);
                                        }
                                    }
                                    for (Field dataField : entityData.getClass().getDeclaredFields()) {
                                        if (dataField.getType() == boolean.class) {
                                            dataField.setAccessible(true);
                                            dataField.setBoolean(entityData, true);
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static DamageSource getErosionSource(Level level, @Nullable Entity attacker, @Nullable String customDeathMessage) {
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