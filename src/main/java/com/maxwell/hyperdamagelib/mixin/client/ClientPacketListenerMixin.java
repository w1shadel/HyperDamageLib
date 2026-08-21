package com.maxwell.hyperdamagelib.mixin.client;

import com.maxwell.hyperdamagelib.util.IDecayEntity;
import com.maxwell.hyperdamagelib.util.InvincibleHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
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
        if (this.level == null) return;
        packet.getEntityIds().forEach((id) -> {
            Entity entity = this.level.getEntity(id);
            if (entity instanceof IDecayEntity decay) {
                decay.setRemoveBypass(true);
            }
            if (entity != null) {
                InvincibleHelper.setRemoveBypass(entity, true);
            }
        });
    }

    @Inject(method = "handleRemoveEntities", at = @At("RETURN"))
    public void handleRemoveEntitiesAfterMixin(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        if (this.level == null) return;
        packet.getEntityIds().forEach((id) -> {
            Entity entity = this.level.getEntity(id);
            if (entity instanceof IDecayEntity decay) {
                decay.setRemoveBypass(false);
            }
            if (entity != null) {
                InvincibleHelper.setRemoveBypass(entity, false);
            }
        });
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    public void handleRespawnMixin(ClientboundRespawnPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            InvincibleHelper.CLIENT_REMOVE_BYPASS.remove(mc.player.getUUID());
            if (mc.player instanceof IDecayEntity decay) {
                decay.setSuperInvincible(false);
                decay.setDecayAmount(0.0F);
            }
        }
    }
}