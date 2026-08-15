package com.maxwell.hyperdamagelib.util;

import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.*;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Objects;

public class DecayForceKillHelper {

    public static void decayForceKill(LivingEntity entity) {
        if (entity.level().isClientSide()) return;

        breakBrain(entity);

        if (entity instanceof IDecayEntity decay) {
            decay.setDecayAmount(entity.getMaxHealth() * 2.0F);
        }

        try {
            DecayDamageUtil.BYPASS_DECAY.set(true);
            entity.setHealth(0.0F);
            entity.getEntityData().set(LivingEntityAccessor.getDataHealthId(), 0.0F);
        } finally {
            DecayDamageUtil.BYPASS_DECAY.remove();
        }

        DamageSource erosion = DecayDamageUtil.getErosionSource(entity.level(), entity);
        entity.die(erosion);
        dropAllForce(entity);

        if (!(entity instanceof Player)) {
            if (entity instanceof IDecayEntity decay) {
                decay.setRemoveBypass(true);
            }
            tpRemove(entity);
            removeFromMemory(entity);
        }
    }

    public static void tpRemove(Entity victim) {
        forceSetPosition(victim, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE);
    }

    public static void forceSetPosition(Entity entity, double x, double y, double z) {
        entity.setPosRaw(x, y, z);
        int X = Mth.floor(x);
        int Y = Mth.floor(y);
        int Z = Mth.floor(z);
        entity.blockPosition = new BlockPos(X, Y, Z);
        if (SectionPos.blockToSectionCoord(X) != entity.chunkPosition.x || SectionPos.blockToSectionCoord(Z) != entity.chunkPosition.z) {
            entity.chunkPosition = new ChunkPos(entity.blockPosition);
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
        } catch (Throwable ignored) {}
    }

    public static void breakGoalSelector(GoalSelector goalSelector) {
        try {
            goalSelector.removeAllGoals(goal -> true);
            goalSelector.addGoal(0, new Goal() {
                @Override
                public boolean canUse() { return false; }
            });
        } catch (Throwable ignored) {}
    }

    public static void dropAllForce(LivingEntity livingEntity) {
        if (livingEntity instanceof Player player) {
            player.getInventory().compartments.forEach(itemStacks ->
                    itemStacks.forEach(stack -> clearStackAndDrop(player, stack))
            );
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            clearStackAndDrop(livingEntity, livingEntity.getItemBySlot(slot));
        }
    }

    public static void clearStackAndDrop(Entity entity, ItemStack itemStack) {
        if (itemStack != null && !itemStack.isEmpty()) {
            ItemStack stack = itemStack.copyAndClear();
            entity.spawnAtLocation(stack);
        }
    }

    @SuppressWarnings("unchecked")
    public static void removeFromMemory(Entity victim) {
        Level level = victim.level();
        if (level instanceof ServerLevel serverLevel) {
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