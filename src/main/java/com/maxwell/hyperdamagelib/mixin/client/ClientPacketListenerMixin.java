package com.maxwell.hyperdamagelib.mixin.client;

import com.maxwell.hyperdamagelib.util.IDecayEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Shadow
    private ClientLevel level;

    @Inject(method = "handleRemoveEntities", at = @At("HEAD"))
    public void handleRemoveEntitiesBeforeMixin(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        packet.getEntityIds().forEach((id) -> {
            Entity entity = this.level.getEntity(id);
            if (entity instanceof IDecayEntity decay) {
                decay.setRemoveBypass(true);
            }
        });
    }

    @Inject(method = "handleRemoveEntities", at = @At("RETURN"))
    public void handleRemoveEntitiesAfterMixin(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        packet.getEntityIds().forEach((id) -> {
            Entity entity = this.level.getEntity(id);
            if (entity instanceof IDecayEntity decay) {
                decay.setRemoveBypass(false);
            }
        });
    }
}