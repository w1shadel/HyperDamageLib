package com.maxwell.hyperdamagelib.util;

import net.minecraft.core.SectionPos;
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

import java.util.List;
import java.util.Objects;

public class DecayForceKillHelper {
    public static void decayForceKill(LivingEntity entity) {
        if (entity.level().isClientSide()) return;
        breakBrain(entity);
        if (entity instanceof IDecayEntity decay) {
            decay.setDecayAmount(entity.getMaxHealth());
        }
        try {
            com.maxwell.hyperdamagelib.util.DecayDamageUtil.BYPASS_DECAY.set(true);
            entity.setHealth(0.0F);
        } finally {
            com.maxwell.hyperdamagelib.util.DecayDamageUtil.BYPASS_DECAY.remove();
        }
        DamageSource erosion = DecayDamageUtil.getErosionSource(entity.level(), entity);
        entity.die(erosion);
        dropAllForce(entity);
        if (!(entity instanceof Player)) {
            removeFromMemory(entity);
        }
    }

    public static void breakBrain(LivingEntity entity) {
        entity.getBrain().clearMemories();
        if (entity instanceof Mob mob) {
            breakGoalSelector(mob.goalSelector);
            breakGoalSelector(mob.targetSelector);
            mob.setTarget(null);
        }
    }

    public static void breakGoalSelector(GoalSelector goalSelector) {
        goalSelector.removeAllGoals(goal -> true);
        goalSelector.addGoal(0, new Goal() {
            @Override
            public boolean canUse() {
                return false;
            }
        });
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

    public static void removeFromMemory(Entity victim) {
        Level level = victim.level();
        if (level instanceof ServerLevel serverLevel) {
            PersistentEntitySectionManager<Entity> entityManager = serverLevel.entityManager;
            victim.levelCallback.onRemove(Entity.RemovalReason.KILLED);
            victim.levelCallback = EntityInLevelCallback.NULL;
            removeFromPersistentEntityManager(entityManager, victim);
            serverLevel.getChunkSource().removeEntity(victim);
        }
    }

    @SuppressWarnings("unchecked")
    public static void removeFromPersistentEntityManager(PersistentEntitySectionManager<Entity> manager, Entity victim) {
        EntitySectionStorage<Entity> sectionStorage = manager.sectionStorage;
        if (manager.isLoaded(victim.getUUID())) {
            long index = SectionPos.of(victim.blockPosition()).asLong();
            EntitySection<Entity> tSection = sectionStorage.getSection(index);
            if (Objects.nonNull(tSection)) {
                EntitySection<Entity> newSection = new EntitySection<>(Entity.class, tSection.getStatus());
                List<Entity> entities = tSection.getEntities()
                        .filter(entity -> victim != entity)
                        .toList();
                for (Entity entity : entities) {
                    newSection.add(entity);
                }
                sectionStorage.sections.replace(index, newSection);
            }
            manager.knownUuids.remove(victim.getUUID());
        }
        EntityLookup<Entity> entityLookup = manager.visibleEntityStorage;
        entityLookup.remove(victim);
        if (entityLookup.getEntity(victim.getId()) != null) {
            EntityLookup<Entity> newEntityLookup = new EntityLookup<>();
            for (Entity entity : entityLookup.getAllEntities()) {
                if (entity != victim) {
                    newEntityLookup.add(entity);
                }
            }
            manager.visibleEntityStorage = newEntityLookup;
            manager.entityGetter = new LevelEntityGetterAdapter<>(newEntityLookup, sectionStorage);
        }
    }
}