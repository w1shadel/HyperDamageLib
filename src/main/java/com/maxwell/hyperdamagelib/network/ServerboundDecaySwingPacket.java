package com.maxwell.hyperdamagelib.network.client;

import com.maxwell.hyperdamagelib.item.DecaySword;
import com.maxwell.hyperdamagelib.item.ErosionSwordItem;
import com.maxwell.hyperdamagelib.item.PenetrateSwordItem;
import com.maxwell.hyperdamagelib.util.DecayDamageUtil;
import com.maxwell.hyperdamagelib.util.DecayForceKillHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public class ServerboundDecaySwingPacket {
    public ServerboundDecaySwingPacket() {
    }

    public static void encode(ServerboundDecaySwingPacket msg, FriendlyByteBuf buf) {
    }

    public static ServerboundDecaySwingPacket decode(FriendlyByteBuf buf) {
        return new ServerboundDecaySwingPacket();
    }

    public static void handle(ServerboundDecaySwingPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || player.level().isClientSide()) return;
            ItemStack held = player.getMainHandItem();
            double range = 5.0D;
            Vec3 eyePos = player.getEyePosition(1.0F);
            Vec3 lookVec = player.getLookAngle();
            AABB searchBox = player.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(2.0D);
            List<LivingEntity> targets = player.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                    e -> e != player && !e.isSpectator());
            for (LivingEntity target : targets) {
                Vec3 toTarget = target.getEyePosition(1.0F).subtract(eyePos);
                if (toTarget.length() <= range && lookVec.dot(toTarget.normalize()) > 0.30D) {
                    if (held.getItem() instanceof ErosionSwordItem) {
                        DamageSource source = DecayDamageUtil.getErosionSource(player.level(), player);
                        DecayDamageUtil.applyCustomDamage(target, source, 30.0F);
                    } else if (held.getItem() instanceof PenetrateSwordItem) {
                        DamageSource source = DecayDamageUtil.getPenetrateSource(player.level(), player);
                        DecayDamageUtil.applyCustomDamage(target, source, 18.0F);
                    } else if (held.getItem() instanceof DecaySword) {
                        DecayForceKillHelper.decayForceKill(target);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}