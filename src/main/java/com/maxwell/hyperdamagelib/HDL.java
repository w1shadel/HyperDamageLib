package com.maxwell.hyperdamagelib;

import com.maxwell.hyperdamagelib.client.util.DecayClientSetup;
import com.maxwell.hyperdamagelib.init.ModItems;
import com.maxwell.hyperdamagelib.init.ModTabs;
import com.maxwell.hyperdamagelib.network.ModMessages;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(HDL.MODID)
public class HDL {
    public static final String MODID = "hyperdamagelib";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public HDL(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        ModItems.ITEMS.register(modEventBus);
        ModTabs.CREATIVE_TABS.register(modEventBus);
        modEventBus.addListener(this::addCreativeContents);
        modEventBus.addListener(this::commonSetup);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(DecayClientSetup::onClientSetup);
        }
    }

    public static ResourceLocation getResourceLocation(String path) {
        return new ResourceLocation(MODID, path);
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