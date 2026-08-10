package com.maxwell.hyperdamagelib.item;

import com.maxwell.hyperdamagelib.entity.MeasurementDummyEntity;
import com.maxwell.hyperdamagelib.init.ModEntities;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

public class DummyControllerItem extends Item {
    public DummyControllerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Vec3 clickPos = context.getClickLocation();
        MeasurementDummyEntity dummy = ModEntities.MEASUREMENT_DUMMY.get().create(context.getLevel());
        if (dummy != null) {
            dummy.moveTo(clickPos.x, clickPos.y, clickPos.z, 0.0F, 0.0F);
            context.getLevel().addFreshEntity(dummy);
            player.displayClientMessage(Component.translatable("message.hyperdamagelib.dummy.spawned"), true);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (target.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (target instanceof MeasurementDummyEntity dummy) {
            if (player.isShiftKeyDown()) {
                dummy.setRemoveBypass(true);
                com.maxwell.hyperdamagelib.util.DummyWatchdog.ACTIVE_DUMMIES.remove(dummy.getUUID());
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    ItemStack eq = dummy.getItemBySlot(slot);
                    if (!eq.isEmpty()) {
                        player.getInventory().placeItemBackInInventory(eq);
                    }
                }
                dummy.discard();
                if (!player.getAbilities().instabuild) {
                    player.getInventory().placeItemBackInInventory(new ItemStack(this));
                    stack.shrink(1);
                }
                player.displayClientMessage(Component.translatable("message.hyperdamagelib.dummy.recovered"), true);
                return InteractionResult.SUCCESS;
            } else {
                dummy.forceResetStats();
                player.displayClientMessage(Component.translatable("message.hyperdamagelib.dummy.reset"), true);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }
}