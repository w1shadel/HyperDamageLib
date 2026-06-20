package com.maxwell.hyperdamagelib;

import com.maxwell.hyperdamagelib.init.ModItems;
import com.maxwell.hyperdamagelib.init.ModTabs;
import com.maxwell.hyperdamagelib.network.ModMessages;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(HDL.MODID)
public class HDL
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "hyperdamagelib";
    public static ResourceLocation getResourceLocation(String path) {
        return new ResourceLocation(MODID, path);
    }
    public HDL(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();
        ModItems.ITEMS.register(modEventBus);
        ModTabs.CREATIVE_TABS.register(modEventBus);
        modEventBus.addListener(this::addCreativeContents);
        modEventBus.addListener(this::commonSetup);
    }

    private void addCreativeContents(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == ModTabs.PRIME_TAB.get()) {
            ModItems.ITEMS.getEntries().forEach(item -> event.accept(item.get()));
        }
    }

    private void commonSetup(final net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(ModMessages::register);
    }
}
