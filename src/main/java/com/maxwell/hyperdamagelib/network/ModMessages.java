package com.maxwell.hyperdamagelib.network;

import com.maxwell.hyperdamagelib.HDL;
import com.maxwell.hyperdamagelib.network.client.ClientboundDecayEffectPacket;
import com.maxwell.hyperdamagelib.network.client.ClientboundDecaySyncPacket;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

@SuppressWarnings("removal")
public class ModMessages {
    public static final String VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.ChannelBuilder
            .named(HDL.getResourceLocation("main"))
            .clientAcceptedVersions(VERSION::equals)
            .serverAcceptedVersions(VERSION::equals)
            .networkProtocolVersion(() -> VERSION)
            .simpleChannel();
    private static int packetId = 0;

    private static int id() {
        return packetId++;
    }

    public static void register() {
        INSTANCE.messageBuilder(ClientboundDecaySyncPacket.class, id())
                .encoder(ClientboundDecaySyncPacket::encode)
                .decoder(ClientboundDecaySyncPacket::decode)
                .consumerMainThread(ClientboundDecaySyncPacket::handle)
                .add();
        INSTANCE.messageBuilder(ClientboundDecayEffectPacket.class, id())
                .encoder(ClientboundDecayEffectPacket::encode)
                .decoder(ClientboundDecayEffectPacket::decode)
                .consumerMainThread(ClientboundDecayEffectPacket::handle)
                .add();
    }

    public static <MSG> void sendToClients(MSG message) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), message);
    }

    public static <MSG> void sendToPlayer(MSG message, net.minecraft.server.level.ServerPlayer player) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
