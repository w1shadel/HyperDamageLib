package com.maxwell.hyperdamagelib.network.client;

import com.maxwell.hyperdamagelib.client.util.DecayClientEffectHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ClientboundDecayEffectPacket {
    private final int duration;
    private final float intensity;
    private final float scaleX;
    private final float scaleY;
    private final float spinSpeed;
    private final float waveAmplitude;
    private final float waveSpeed;

    public ClientboundDecayEffectPacket(int duration, float intensity, float scaleX, float scaleY, float spinSpeed, float waveAmplitude, float waveSpeed) {
        this.duration = duration;
        this.intensity = intensity;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.spinSpeed = spinSpeed;
        this.waveAmplitude = waveAmplitude;
        this.waveSpeed = waveSpeed;
    }

    public static void encode(ClientboundDecayEffectPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.duration);
        buf.writeFloat(msg.intensity);
        buf.writeFloat(msg.scaleX);
        buf.writeFloat(msg.scaleY);
        buf.writeFloat(msg.spinSpeed);
        buf.writeFloat(msg.waveAmplitude);
        buf.writeFloat(msg.waveSpeed);
    }

    public static ClientboundDecayEffectPacket decode(FriendlyByteBuf buf) {
        return new ClientboundDecayEffectPacket(
                buf.readInt(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat()
        );
    }

    public static void handle(ClientboundDecayEffectPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                msg.handleClient();
            }
        });
        ctx.get().setPacketHandled(true);
    }

    @OnlyIn(Dist.CLIENT)
    private void handleClient() {
        DecayClientEffectHelper.triggerCustomEffect(
                this.duration, this.intensity, this.scaleX, this.scaleY, this.spinSpeed, this.waveAmplitude, this.waveSpeed
        );
    }
}