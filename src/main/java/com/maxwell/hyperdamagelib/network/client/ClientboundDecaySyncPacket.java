package com.maxwell.hyperdamagelib.network.client;

import com.maxwell.hyperdamagelib.mixin.accessor.LivingEntityAccessor;
import com.maxwell.hyperdamagelib.util.InvincibleHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientboundDecaySyncPacket {
    private final int entityId;
    private final boolean superInvincible;
    private final boolean keepCurrentHealth;
    private final float invincibleHealthValue;
    private final boolean healBlocked;

    public ClientboundDecaySyncPacket(int entityId, boolean superInvincible, boolean keepCurrentHealth, float invincibleHealthValue, boolean healBlocked) {
        this.entityId = entityId;
        this.superInvincible = superInvincible;
        this.keepCurrentHealth = keepCurrentHealth;
        this.invincibleHealthValue = invincibleHealthValue;
        this.healBlocked = healBlocked;
    }

    public static void encode(ClientboundDecaySyncPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeBoolean(msg.superInvincible);
        buf.writeBoolean(msg.keepCurrentHealth);
        buf.writeFloat(msg.invincibleHealthValue);
        buf.writeBoolean(msg.healBlocked);
    }

    public static ClientboundDecaySyncPacket decode(FriendlyByteBuf buf) {
        return new ClientboundDecaySyncPacket(
                buf.readInt(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readFloat(),
                buf.readBoolean()
        );
    }

    public static void handle(ClientboundDecaySyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                msg.handleClient();
            }
        });
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private void handleClient() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Entity entity = mc.level.getEntity(this.entityId);
            if (entity != null) {
                InvincibleHelper.setInvincible(entity, this.superInvincible);
                InvincibleHelper.setHealBlocked(entity, this.healBlocked);
                if (entity instanceof LivingEntity living) {
                    InvincibleHelper.setInvincibleHealth(living, this.invincibleHealthValue);
                    if (this.superInvincible) {
                        living.dead = false;
                        living.deathTime = 0;
                        if (living.getPose() == Pose.DYING) {
                            living.setPose(Pose.STANDING);
                        }
                        living.setHealth(this.invincibleHealthValue > 0 ? this.invincibleHealthValue : 20.0F);
                        try {
                            living.getEntityData().set(LivingEntityAccessor.getDataHealthId(), this.invincibleHealthValue > 0 ? this.invincibleHealthValue : 20.0F);
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        }
    }
}