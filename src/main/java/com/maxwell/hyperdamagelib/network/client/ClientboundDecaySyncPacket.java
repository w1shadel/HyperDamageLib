package com.maxwell.hyperdamagelib.network.client;

import com.maxwell.hyperdamagelib.util.IDecayEntity;
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
    private final float decayAmount;
    private final boolean superInvincible;
    private final boolean keepCurrentHealth;
    private final float invincibleHealthValue;
    private final boolean healBlocked;

    public ClientboundDecaySyncPacket(int entityId, float decayAmount, boolean superInvincible, boolean keepCurrentHealth, float invincibleHealthValue, boolean healBlocked) {
        this.entityId = entityId;
        this.decayAmount = decayAmount;
        this.superInvincible = superInvincible;
        this.keepCurrentHealth = keepCurrentHealth;
        this.invincibleHealthValue = invincibleHealthValue;
        this.healBlocked = healBlocked;
    }

    public static void encode(ClientboundDecaySyncPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeFloat(msg.decayAmount);
        buf.writeBoolean(msg.superInvincible);
        buf.writeBoolean(msg.keepCurrentHealth);
        buf.writeFloat(msg.invincibleHealthValue);
        buf.writeBoolean(msg.healBlocked);
    }

    public static ClientboundDecaySyncPacket decode(FriendlyByteBuf buf) {
        return new ClientboundDecaySyncPacket(
                buf.readInt(),
                buf.readFloat(),
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
            if (entity instanceof IDecayEntity decay) {
                decay.setDecayAmount(this.decayAmount);
                decay.setKeepCurrentHealth(this.keepCurrentHealth);
                decay.setInvincibleHealthValue(this.invincibleHealthValue);
                decay.setSuperInvincible(this.superInvincible);
                decay.setHealBlocked(this.healBlocked);
                InvincibleHelper.setInvincible(entity, this.superInvincible);
                if (this.superInvincible && entity instanceof LivingEntity living) {
                    living.dead = false;
                    living.deathTime = 0;
                    if (living.getPose() == Pose.DYING) {
                        living.setPose(Pose.STANDING);
                    }
                }
            }
        }
    }
}