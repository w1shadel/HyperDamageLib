package com.maxwell.hyperdamagelib.init;

import com.maxwell.hyperdamagelib.HDL;
import com.maxwell.hyperdamagelib.item.ErosionSwordItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, HDL.MODID);
    public static final RegistryObject<Item> EROSION_SWORD = ITEMS.register("erosion_sword", () ->
            new ErosionSwordItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant())
    );
}