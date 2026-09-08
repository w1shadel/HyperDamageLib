package com.maxwell.hyperdamagelib.entity;

import com.maxwell.hyperdamagelib.init.ModItems;
import com.maxwell.hyperdamagelib.util.InvincibleHelper;
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
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MeasurementDummyEntity extends Mob {
    private final List<Record> damageRecords = new ArrayList<>();
    private final List<Record> healRecords = new ArrayList<>();
    private long lastDamageTime = 0;
    private long sessionStartTime = 0;
    private float totalDamageSession = 0.0F;
    private float totalHealSession = 0.0F;
    private float lastDamageAmount = 0.0F;
    private UUID lastAttackerUuid = null;
    private boolean removeBypass = false;

    public MeasurementDummyEntity(EntityType<? extends Mob> type, Level level) {
        super(type, level);
        this.setNoGravity(false);
        this.setInvulnerable(false);
        InvincibleHelper.setInvincible(this, true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1000000.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.ARMOR, 0.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 0.0D);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide() || this.removeBypass) {
            return super.hurt(source, amount);
        }
        recordDamageAbsolute(source, amount);
        return true;
    }

    public void recordDamageAbsolute(DamageSource source, float amount) {
        if (this.level().isClientSide() || amount <= 0.0F || Float.isNaN(amount)) return;
        long now = System.currentTimeMillis();
        if (this.damageRecords.isEmpty()) {
            this.sessionStartTime = now;
        }
        this.lastDamageTime = now;
        this.lastDamageAmount = amount;
        this.totalDamageSession += amount;
        this.damageRecords.add(new Record(amount, now));
        Entity attacker = source.getEntity();
        if (attacker instanceof ServerPlayer player) {
            this.lastAttackerUuid = player.getUUID();
            updateActionBar(player);
        } else if (this.lastAttackerUuid != null) {
            Player player = this.level().getPlayerByUUID(this.lastAttackerUuid);
            if (player instanceof ServerPlayer sp) updateActionBar(sp);
        }
        this.level().broadcastDamageEvent(this, source);
        this.setDeltaMovement(Vec3.ZERO);
        this.hurtMarked = false;
        super.setHealth(this.getMaxHealth());
    }

    @Override
    public void heal(float amount) {
        if (this.level().isClientSide() || amount <= 0.0F || Float.isNaN(amount)) return;
        long now = System.currentTimeMillis();
        this.totalHealSession += amount;
        this.healRecords.add(new Record(amount, now));
        if (this.lastAttackerUuid != null) {
            Player player = this.level().getPlayerByUUID(this.lastAttackerUuid);
            if (player instanceof ServerPlayer sp) updateActionBar(sp);
        }
        super.heal(amount);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;
        if (!this.removeBypass) {
            this.dead = false;
            this.deathTime = 0;
        }
        long now = System.currentTimeMillis();
        cleanupOldRecords(now);
        if (this.totalDamageSession > 0 && (now - this.lastDamageTime > 3000)) {
            sendSessionSummary();
            forceResetStats();
        }
        if (this.getY() < this.level().getMinBuildHeight() - 32.0D) {
            net.minecraft.core.BlockPos sharedSpawn = this.level().getSharedSpawnPos();
            this.teleportTo(sharedSpawn.getX() + 0.5D, sharedSpawn.getY() + 2.0D, sharedSpawn.getZ() + 0.5D);
            this.setDeltaMovement(Vec3.ZERO);
        }
        this.setYRot(0.0F);
        this.setXRot(0.0F);
    }

    private void updateActionBar(ServerPlayer player) {
        long now = System.currentTimeMillis();
        float dps = calculateDPS(now);
        float hps = calculateHPS(now);
        Component actionBarMsg = Component.translatable("commands.hdl.dummy.action_bar",
                String.format("%.1f", this.lastDamageAmount),
                String.format("%.1f", dps),
                String.format("%.1f", hps),
                String.format("%.1f", this.totalDamageSession)
        );
        player.sendSystemMessage(actionBarMsg, true);
    }

    private void sendSessionSummary() {
        if (this.lastAttackerUuid == null) return;
        Player player = this.level().getPlayerByUUID(this.lastAttackerUuid);
        if (player instanceof ServerPlayer sp) {
            long durationMs = this.lastDamageTime - this.sessionStartTime;
            float durationSecs = Math.max(1.0F, durationMs / 1000.0F);
            float avgDps = this.totalDamageSession / durationSecs;
            sp.sendSystemMessage(Component.translatable("commands.hdl.dummy.summary.header"));
            sp.sendSystemMessage(Component.translatable("commands.hdl.dummy.summary.total", String.format("%.1f", this.totalDamageSession)));
            sp.sendSystemMessage(Component.translatable("commands.hdl.dummy.summary.duration", String.format("%.1f", durationSecs)));
            sp.sendSystemMessage(Component.translatable("commands.hdl.dummy.summary.avg_dps", String.format("%.1f", avgDps)));
            sp.sendSystemMessage(Component.translatable("commands.hdl.dummy.summary.footer"));
        }
    }

    private void cleanupOldRecords(long now) {
        this.damageRecords.removeIf(r -> now - r.timestamp > 10000);
        this.healRecords.removeIf(r -> now - r.timestamp > 10000);
    }

    private float calculateDPS(long now) {
        if (this.damageRecords.isEmpty()) return 0.0F;
        float sum = 0;
        long minTime = now;
        for (Record r : this.damageRecords) {
            sum += r.amount;
            if (r.timestamp < minTime) minTime = r.timestamp;
        }
        float seconds = Math.max(1.0F, (now - minTime) / 1000.0F);
        return sum / seconds;
    }

    private float calculateHPS(long now) {
        if (this.healRecords.isEmpty()) return 0.0F;
        float sum = 0;
        long minTime = now;
        for (Record r : this.healRecords) {
            sum += r.amount;
            if (r.timestamp < minTime) minTime = r.timestamp;
        }
        float seconds = Math.max(1.0F, (now - minTime) / 1000.0F);
        return sum / seconds;
    }

    public void forceResetStats() {
        this.totalDamageSession = 0.0F;
        this.totalHealSession = 0.0F;
        this.lastDamageAmount = 0.0F;
        this.damageRecords.clear();
        this.healRecords.clear();
        this.lastAttackerUuid = null;
    }

    @Override
    public void setHealth(float health) {
        if (this.removeBypass) super.setHealth(health);
        else super.setHealth(this.getMaxHealth());
    }

    @Override
    public void die(DamageSource cause) {
        if (this.removeBypass) super.die(cause);
    }

    @Override
    public void kill() {
        if (this.removeBypass) super.kill();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (reason.shouldDestroy()) {
            if (this.removeBypass) super.remove(reason);
        } else {
            super.remove(reason);
        }
    }

    @Override
    public boolean isDeadOrDying() {
        return this.removeBypass && super.isDeadOrDying();
    }

    @Override
    public boolean isAlive() {
        return !this.removeBypass || super.isAlive();
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == 3 && !this.removeBypass) return;
        super.handleEntityEvent(id);
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
            if (!player.getAbilities().instabuild) held.shrink(1);
            if (!current.isEmpty()) player.getInventory().placeItemBackInInventory(current);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private EquipmentSlot getArmorSlotForItem(ItemStack stack) {
        if (stack.isEmpty()) return null;
        EquipmentSlot slot = stack.getEquipmentSlot();
        if (slot == null) {
            Equipable equipable = Equipable.get(stack);
            if (equipable != null) slot = equipable.getEquipmentSlot();
        }
        return (slot != null && slot.getType() == EquipmentSlot.Type.ARMOR) ? slot : null;
    }

    public boolean isRemoveBypass() {
        return this.removeBypass;
    }

    public void setRemoveBypass(boolean val) {
        this.removeBypass = val;
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    public void push(double x, double y, double z) {
    }

    private static class Record {
        final float amount;
        final long timestamp;

        Record(float amount, long timestamp) {
            this.amount = amount;
            this.timestamp = timestamp;
        }
    }
}