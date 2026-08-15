package com.maxwell.hyperdamagelib.entity;

import com.maxwell.hyperdamagelib.init.ModItems;
import com.maxwell.hyperdamagelib.util.IDecayEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MeasurementDummyEntity extends Mob implements IDecayEntity {
    private final List<DamageRecord> damageRecords = new ArrayList<>();
    private final List<HealRecord> healRecords = new ArrayList<>();
    private final NonNullList<ItemStack> armorItems = NonNullList.withSize(4, ItemStack.EMPTY);
    private final NonNullList<ItemStack> handItems = NonNullList.withSize(2, ItemStack.EMPTY);
    private long lastDamageTime = 0;
    private float totalDamageInSession = 0;
    private UUID lastAttackerUuid = null;
    private boolean removeBypass = false;

    public MeasurementDummyEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setNoGravity(false);
        this.setInvulnerable(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1000000.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D);
    }

    @Override
    public boolean isRemoveBypass() {
        return this.removeBypass;
    }

    @Override
    public void setRemoveBypass(boolean val) {
        this.removeBypass = val;
    }

    public void recordDamageAbsolute(DamageSource source, float amount) {
        if (this.level().isClientSide() || this.isRemoveBypass() || amount <= 0.0F || Float.isNaN(amount)) {
            return;
        }
        long now = System.currentTimeMillis();
        this.lastDamageTime = now;
        this.damageRecords.add(new DamageRecord(amount, now));
        this.totalDamageInSession += amount;
        Entity attacker = source.getEntity();
        if (attacker instanceof Player player) {
            this.lastAttackerUuid = player.getUUID();
            updateActionBar(player);
        }
        this.level().broadcastDamageEvent(this, source);
        this.setDeltaMovement(Vec3.ZERO);
        this.hurtMarked = false;
        this.setHealth(this.getMaxHealth());
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide() || this.isRemoveBypass()) {
            return super.hurt(source, amount);
        }
        recordDamageAbsolute(source, amount);
        return true;
    }

    @Override
    protected void actuallyHurt(DamageSource source, float amount) {
        if (this.isRemoveBypass()) {
            super.actuallyHurt(source, amount);
        }
    }

    @Override
    public void heal(float amount) {
        if (this.level().isClientSide() || amount <= 0.0F || Float.isNaN(amount)) {
            return;
        }
        long now = System.currentTimeMillis();
        this.healRecords.add(new HealRecord(amount, now));
        if (this.lastAttackerUuid != null) {
            Player player = this.level().getPlayerByUUID(this.lastAttackerUuid);
            if (player != null) {
                updateActionBar(player);
            }
        }
        super.heal(amount);
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide()) {
            this.dead = false;
            this.deathTime = 0;
        }
        super.tick();
        if (!this.level().isClientSide()) {
            long now = System.currentTimeMillis();
            cleanupRecords(now);
            if (this.totalDamageInSession > 0 && (now - this.lastDamageTime > 3000)) {
                sendSessionSummary();
                this.totalDamageInSession = 0;
                this.damageRecords.clear();
                this.healRecords.clear();
                this.lastAttackerUuid = null;
            }
            if (this.getY() < this.level().getMinBuildHeight() - 32.0D) {
                net.minecraft.core.BlockPos sharedSpawn = this.level().getSharedSpawnPos();
                this.teleportTo(sharedSpawn.getX() + 0.5D, sharedSpawn.getY() + 2.0D, sharedSpawn.getZ() + 0.5D);
                this.setDeltaMovement(Vec3.ZERO);
                this.fallDistance = 0.0F;
            }
            this.setYRot(0.0F);
            this.setXRot(0.0F);
            this.yRotO = 0.0F;
            this.xRotO = 0.0F;
        }
    }

    private void cleanupRecords(long now) {
        this.damageRecords.removeIf(record -> now - record.timestamp > 10000);
        this.healRecords.removeIf(record -> now - record.timestamp > 10000);
    }

    private float calculateDPS(long now) {
        if (this.damageRecords.isEmpty()) return 0.0F;
        long minTime = now;
        long maxTime = 0;
        float sum = 0;
        for (DamageRecord record : this.damageRecords) {
            sum += record.amount;
            if (record.timestamp < minTime) minTime = record.timestamp;
            if (record.timestamp > maxTime) maxTime = record.timestamp;
        }
        float seconds = (maxTime - minTime) / 1000.0F;
        if (seconds < 0.5F) seconds = 1.0F;
        return sum / seconds;
    }

    private float calculateHPS(long now) {
        if (this.healRecords.isEmpty()) return 0.0F;
        long minTime = now;
        long maxTime = 0;
        float sum = 0;
        for (HealRecord record : this.healRecords) {
            sum += record.amount;
            if (record.timestamp < minTime) minTime = record.timestamp;
            if (record.timestamp > maxTime) maxTime = record.timestamp;
        }
        float seconds = (maxTime - minTime) / 1000.0F;
        if (seconds < 0.5F) seconds = 1.0F;
        return sum / seconds;
    }

    private void updateActionBar(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            long now = System.currentTimeMillis();
            float dps = calculateDPS(now);
            float hps = calculateHPS(now);
            float lastDamage = this.damageRecords.isEmpty() ? 0.0F : this.damageRecords.get(this.damageRecords.size() - 1).amount;
            Component actionBarMsg = Component.translatable("commands.hdl.dummy.action_bar",
                    String.format("%.1f", lastDamage),
                    String.format("%.1f", dps),
                    String.format("%.1f", hps),
                    String.format("%.1f", this.totalDamageInSession)
            );
            serverPlayer.sendSystemMessage(actionBarMsg, true);
        }
    }

    private void sendSessionSummary() {
        if (this.lastAttackerUuid != null) {
            Player player = this.level().getPlayerByUUID(this.lastAttackerUuid);
            if (player instanceof ServerPlayer serverPlayer) {
                long durationMs = this.lastDamageTime - (this.damageRecords.isEmpty() ? this.lastDamageTime : this.damageRecords.get(0).timestamp);
                float durationSecs = durationMs / 1000.0F;
                if (durationSecs < 1.0F) durationSecs = 1.0F;
                float avgDps = this.totalDamageInSession / durationSecs;
                serverPlayer.sendSystemMessage(Component.translatable("commands.hdl.dummy.summary.header"));
                serverPlayer.sendSystemMessage(Component.translatable("commands.hdl.dummy.summary.total", String.format("%.1f", this.totalDamageInSession)));
                serverPlayer.sendSystemMessage(Component.translatable("commands.hdl.dummy.summary.duration", String.format("%.1f", durationSecs)));
                serverPlayer.sendSystemMessage(Component.translatable("commands.hdl.dummy.summary.avg_dps", String.format("%.1f", avgDps)));
                serverPlayer.sendSystemMessage(Component.translatable("commands.hdl.dummy.summary.footer"));
            }
        }
    }

    public void forceResetStats() {
        this.totalDamageInSession = 0;
        this.damageRecords.clear();
        this.healRecords.clear();
        this.lastAttackerUuid = null;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() == ModItems.DUMMY_CONTROLLER.get()) {
            return InteractionResult.PASS;
        }
        EquipmentSlot slot = getArmorSlotForItem(held);
        if (slot != null) {
            ItemStack current = this.getItemBySlot(slot);
            this.setItemSlot(slot, held.copy());
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            if (!current.isEmpty()) {
                player.getInventory().placeItemBackInInventory(current);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private EquipmentSlot getArmorSlotForItem(ItemStack pItem) {
        if (pItem.isEmpty()) return null;
        EquipmentSlot slot = pItem.getEquipmentSlot();
        if (slot == null) {
            net.minecraft.world.item.Equipable equipable = net.minecraft.world.item.Equipable.get(pItem);
            if (equipable != null) {
                slot = equipable.getEquipmentSlot();
            }
        }
        if (slot != null && slot.getType() == EquipmentSlot.Type.ARMOR) {
            return slot;
        }
        return null;
    }

    @Override
    public void setHealth(float health) {
        if (this.isRemoveBypass()) {
            super.setHealth(health);
        } else {
            super.setHealth(this.getMaxHealth());
        }
    }

    @Override
    public void die(DamageSource cause) {
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (reason == Entity.RemovalReason.UNLOADED_TO_CHUNK || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER || reason == Entity.RemovalReason.CHANGED_DIMENSION) {
            this.setRemoveBypass(true);
            super.remove(reason);
            this.setRemoveBypass(false);
            return;
        }
        if (this.isRemoveBypass()) {
            super.remove(reason);
        }
    }

    @Override
    public boolean isDeadOrDying() {
        return this.isRemoveBypass() ? super.isDeadOrDying() : false;
    }

    @Override
    public boolean isAlive() {
        return this.isRemoveBypass() ? super.isAlive() : true;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 3) {
            return;
        }
        super.handleEntityEvent(id);
    }

    @Override
    public void onRemovedFromWorld() {
        if (this.isRemoveBypass()) {
            super.onRemovedFromWorld();
            return;
        }
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        this.setRemoveBypass(false);
        if (!this.level().isClientSide()) {
            updateWatchdogData();
        }
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        super.setItemSlot(slot, stack);
        if (!this.level().isClientSide()) {
            updateWatchdogData();
        }
    }

    public void updateWatchdogData() {
        if (this.level().isClientSide()) return;
        NonNullList<ItemStack> armorCopy = NonNullList.withSize(4, ItemStack.EMPTY);
        NonNullList<ItemStack> handsCopy = NonNullList.withSize(2, ItemStack.EMPTY);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = this.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                if (slot.getType() == EquipmentSlot.Type.ARMOR) {
                    armorCopy.set(slot.getIndex(), stack.copy());
                } else {
                    handsCopy.set(slot.getIndex(), stack.copy());
                }
            }
        }
        com.maxwell.hyperdamagelib.util.DummyWatchdog.ACTIVE_DUMMIES.put(this.getUUID(),
                new com.maxwell.hyperdamagelib.util.DummyWatchdog.DummyData(
                        this.getUUID(),
                        this.level().dimension(),
                        this.getX(), this.getY(), this.getZ(),
                        this.getYRot(), this.getXRot(),
                        armorCopy, handsCopy
                )
        );
    }

    @Override
    public void teleportTo(double x, double y, double z) {
        if (this.isRemoveBypass()) {
            super.teleportTo(x, y, z);
            return;
        }
        double maxBoundary = 29999984.0D;
        if (Math.abs(x) > maxBoundary || Math.abs(z) > maxBoundary) {
            return;
        }
        super.teleportTo(x, y, z);
        if (!this.level().isClientSide()) {
            updateWatchdogData();
        }
    }

    @Override
    public Entity changeDimension(net.minecraft.server.level.ServerLevel destination, net.minecraftforge.common.util.ITeleporter teleporter) {
        if (this.isRemoveBypass()) {
            return super.changeDimension(destination, teleporter);
        }
        return this;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public void push(double x, double y, double z) {
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public void travel(net.minecraft.world.phys.Vec3 travelVector) {
        if (this.isEffectiveAi() || this.isControlledByLocalInstance()) {
            super.travel(travelVector);
        } else {
            this.setDeltaMovement(Vec3.ZERO);
        }
    }

    private static class DamageRecord {
        final float amount;
        final long timestamp;

        DamageRecord(float amount, long timestamp) {
            this.amount = amount;
            this.timestamp = timestamp;
        }
    }

    private static class HealRecord {
        final float amount;
        final long timestamp;

        HealRecord(float amount, long timestamp) {
            this.amount = amount;
            this.timestamp = timestamp;
        }
    }
}