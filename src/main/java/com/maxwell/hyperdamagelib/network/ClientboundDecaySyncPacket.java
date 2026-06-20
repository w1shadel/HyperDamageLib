package com.maxwell.hyperdamagelib.network;

import com.maxwell.hyperdamagelib.util.IDecayEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientboundDecaySyncPacket {
    private final int entityId;
    private final float decayAmount;
    private final boolean superInvincible;

    public ClientboundDecaySyncPacket(int entityId, float decayAmount, boolean superInvincible) {
        this.entityId = entityId;
        this.decayAmount = decayAmount;
        this.superInvincible = superInvincible;
    }

    public static void encode(ClientboundDecaySyncPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeFloat(msg.decayAmount);
        buf.writeBoolean(msg.superInvincible);
    }

    public static ClientboundDecaySyncPacket decode(FriendlyByteBuf buf) {
        return new ClientboundDecaySyncPacket(buf.readInt(), buf.readFloat(), buf.readBoolean());
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
                decay.setSuperInvincible(this.superInvincible);
            }
        }
    }
}