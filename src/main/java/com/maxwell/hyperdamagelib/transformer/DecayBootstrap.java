package com.maxwell.hyperdamagelib.transformer;

import com.maxwell.hyperdamagelib.HDL;
import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;

import java.lang.reflect.Field;
import java.util.Map;

public final class DecayBootstrap {
    public static volatile boolean LAUNCH_PLUGIN_AVAILABLE = false;
    private static volatile boolean STARTED = false;

    private DecayBootstrap() {
    }

    public static void start() {
        if (STARTED) return;
        synchronized (DecayBootstrap.class) {
            if (STARTED) return;
            STARTED = true;
        }
        try {
            if (!LAUNCH_PLUGIN_AVAILABLE) {
                LAUNCH_PLUGIN_AVAILABLE = initLaunchPlugin();
            }
            HDL.LOGGER.info("[HDL] ModLauncher transformation pipeline initialized (CF-Compliant).");
        } catch (Throwable t) {
            HDL.LOGGER.error("[HDL] Critical error during DecayBootstrap.start()", t);
        }
    }

    private static boolean initLaunchPlugin() {
        try {
            ILaunchPluginService plugin = new DecayLaunchPlugin();
            Field field = Launcher.class.getDeclaredField("launchPlugins");
            field.setAccessible(true);
            LaunchPluginHandler pluginHandler = (LaunchPluginHandler) field.get(Launcher.INSTANCE);
            Field pluginsField = LaunchPluginHandler.class.getDeclaredField("plugins");
            pluginsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, ILaunchPluginService> map = (Map<String, ILaunchPluginService>) pluginsField.get(pluginHandler);
            map.put(plugin.name(), plugin);
            return true;
        } catch (Throwable e) {
            HDL.LOGGER.error("[HDL] Failed to inject DecayLaunchPlugin into ModLauncher", e);
            return false;
        }
    }
}