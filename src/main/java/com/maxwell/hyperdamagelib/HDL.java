package com.maxwell.hyperdamagelib;

import com.maxwell.hyperdamagelib.client.event.RegisterDecayAnimatorsEvent;
import com.maxwell.hyperdamagelib.client.init.DecayAnimatorRegistrationListener;
import com.maxwell.hyperdamagelib.init.ModItems;
import com.maxwell.hyperdamagelib.init.ModTabs;
import com.maxwell.hyperdamagelib.network.ModMessages;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(HDL.MODID)
public class HDL {
    public static final String MODID = "hyperdamagelib";

    public HDL(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        ModItems.ITEMS.register(modEventBus);
        ModTabs.CREATIVE_TABS.register(modEventBus);
        modEventBus.addListener(this::addCreativeContents);
        modEventBus.addListener(this::commonSetup);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::clientSetup);
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
    private void clientSetup(final net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            System.out.println("[HDL-DEBUG] Starting FMLClientSetupEvent work...");

            // 登録リスナーを手動でForgeイベントバスに登録
            MinecraftForge.EVENT_BUS.register(DecayAnimatorRegistrationListener.class);
            System.out.println("[HDL-DEBUG] DecayAnimatorRegistrationListener registered manually to Forge EVENT_BUS");

            // 登録イベントの発火
            MinecraftForge.EVENT_BUS.post(new com.maxwell.hyperdamagelib.client.event.RegisterDecayAnimatorsEvent());
            System.out.println("[HDL-DEBUG] RegisterDecayAnimatorsEvent posted to Forge EVENT_BUS!");
        });
    }
}
