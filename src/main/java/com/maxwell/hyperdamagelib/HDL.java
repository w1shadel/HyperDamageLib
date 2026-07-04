package com.maxwell.hyperdamagelib;

import com.maxwell.hyperdamagelib.client.util.DecayClientSetup;
import com.maxwell.hyperdamagelib.init.ModItems;
import com.maxwell.hyperdamagelib.init.ModTabs;
import com.maxwell.hyperdamagelib.network.ModMessages;
import com.maxwell.hyperdamagelib.transformer.DecayBootstrap;
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

    public static void verifyAndRetransform() {
        if (DecayBootstrap.instrumentation == null) return;
        try {
            java.util.List<Class<?>> classesToRetransform = new java.util.ArrayList<>();
            for (Class<?> clazz : DecayBootstrap.instrumentation.getAllLoadedClasses()) {
                String name = clazz.getName();
                if (name.equals("net.minecraft.world.entity.Entity") ||
                        name.equals("net.minecraft.world.entity.LivingEntity") ||
                        name.equals("net.minecraft.network.syncher.SynchedEntityData") ||
                        name.equals("net.minecraft.server.players.PlayerList") ||
                        name.equals("net.minecraft.server.level.ServerPlayer") ||
                        name.equals("net.minecraft.server.level.ServerLevel")) {
                    if (DecayBootstrap.instrumentation.isModifiableClass(clazz)) {
                        classesToRetransform.add(clazz);
                    }
                }
            }
            if (!classesToRetransform.isEmpty()) {
                Class<?>[] classArray = classesToRetransform.toArray(new Class<?>[0]);
                DecayBootstrap.instrumentation.retransformClasses(classArray);
                HDL.LOGGER.info("[HDL] Successfully retransformed " + classesToRetransform.size() + " target classes.");
            }
        } catch (Exception e) {
            HDL.LOGGER.error("[HDL] Failed to bulk-retransform target classes", e);
        }
    }

    private void addCreativeContents(net.minecraftforge.event.BuildCreativeModeTabContentsEvent event) {
        if (event.getTab() == ModTabs.PRIME_TAB.get()) {
            ModItems.ITEMS.getEntries().forEach(item -> event.accept(item.get()));
        }
    }

    private void commonSetup(final net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(ModMessages::register);
        verifyAndRetransform();
    }
}