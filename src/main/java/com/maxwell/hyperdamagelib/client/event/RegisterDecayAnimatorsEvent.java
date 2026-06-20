package com.maxwell.hyperdamagelib.client.event;

import com.maxwell.hyperdamagelib.client.util.DecayItemAnimationRegistry;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.Event;

/**
 * 外部MODからカスタムアイテムのアニメーションを登録するための専用イベントです。
 * Forge Event Bus (MinecraftForge.EVENT_BUS) にて発火されます。
 */
public class RegisterDecayAnimatorsEvent extends Event {

    public void register(Item item, DecayItemAnimationRegistry.IItemAnimator animator) {
        DecayItemAnimationRegistry.register(item, animator);
    }
}