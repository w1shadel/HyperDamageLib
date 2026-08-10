package com.maxwell.hyperdamagelib.transformer;

import com.maxwell.hyperdamagelib.agent.DecayAgent;
import com.maxwell.hyperdamagelib.util.DecayUnsafeHelper;
import com.sun.tools.attach.VirtualMachine;
import cpw.mods.cl.ModuleClassLoader;
import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

public final class DecayBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger("DecayBootstrap");
    private static final String AGENT_CLASS = "com.maxwell.hyperdamagelib.agent.DecayAgent";
    private static final String AGENT_RESOURCE = "com/maxwell/hyperdamagelib/agent/DecayAgent.class";
    private static final String BRIDGE_CLASS = "com.maxwell.hyperdamagelib.agent.DecayBytecodeBridge";
    private static final String BRIDGE_RESOURCE = "com/maxwell/hyperdamagelib/agent/DecayBytecodeBridge.class";
    public static volatile boolean LAUNCH_PLUGIN_AVAILABLE = false;
    static volatile Instrumentation instrumentation = null;
    private static volatile boolean STARTED = false;

    private DecayBootstrap() {
    }

    public static void start() {
        if (STARTED) return;
        synchronized (DecayBootstrap.class) {
            if (STARTED) return;
            STARTED = true;
        }
        DecayEntityMethods.class.getClass();
        DecaySynchedEntityDataMethods.class.getClass();
        try {
            LOGGER.debug("Initialize Decay Transformer Start");
            if (!LAUNCH_PLUGIN_AVAILABLE) {
                LAUNCH_PLUGIN_AVAILABLE = initLaunchPlugin();
            }
            if (instrumentation == null) {
                if (!initAgent()) return;
                instrumentation = fetchInstrumentation();
                if (instrumentation == null) return;
                Class.forName("com.maxwell.hyperdamagelib.transformer.DecayBytecodeGetterTransformer");
                if (!registerBridge()) return;
                instrumentation.addTransformer(new DecayBytecodeGetterTransformer(), true);
                instrumentation.retransformClasses(ModuleClassLoader.class);
            }
        } catch (Throwable t) {
            LOGGER.error("DecayBootstrap.start failed", t);
        }
    }

    private static boolean initLaunchPlugin() {
        try {
            ILaunchPluginService plugin = new DecayLaunchPlugin();
            Field field = Launcher.class.getDeclaredField("launchPlugins");
            DecayUnsafeHelper.forceSetAccessible(field);
            LaunchPluginHandler pluginHandler = (LaunchPluginHandler) field.get(Launcher.INSTANCE);
            field = LaunchPluginHandler.class.getDeclaredField("plugins");
            DecayUnsafeHelper.forceSetAccessible(field);
            @SuppressWarnings("unchecked")
            Map<String, ILaunchPluginService> map = (Map<String, ILaunchPluginService>) field.get(pluginHandler);
            map.put(plugin.name(), plugin);
            return true;
        } catch (NoSuchFieldException | IllegalAccessException e) {
            LOGGER.error("Failed to init launch plugin: " + e);
            return false;
        }
    }

    private static boolean initAgent() {
        try {
            if (!DecayUnsafeHelper.allowAttachSelf()) {
                LOGGER.debug("Could not force attach-self via Unsafe; relying on -Djdk.attach.allowAttachSelf");
            }
            File agentJar = extractAgentJar();
            LOGGER.debug("Agent jar extracted to: {}", agentJar.getAbsolutePath());
            String pid = String.valueOf(ProcessHandle.current().pid());
            VirtualMachine vm = VirtualMachine.attach(pid);
            try {
                vm.loadAgent(agentJar.getAbsolutePath());
            } finally {
                vm.detach();
            }
            return true;
        } catch (Throwable t) {
            LOGGER.error("Agent load failed", t);
            return false;
        }
    }

    private static Instrumentation fetchInstrumentation() {
        try {
            Class<?> agentSys = Class.forName(AGENT_CLASS, true, ClassLoader.getSystemClassLoader());
            return (Instrumentation) agentSys.getField("INSTRUMENTATION").get(null);
        } catch (Throwable t) {
            LOGGER.error("Instrumentation handle unavailable", t);
            return null;
        }
    }

    private static boolean registerBridge() {
        try {
            Class<?> bridgeCls = Class.forName(BRIDGE_CLASS, true, ClassLoader.getSystemClassLoader());
            Field f = bridgeCls.getField("transformer");
            BiFunction<Optional<byte[]>, String, Optional<byte[]>> fn = DecayBytecodeGetterTransformer::transformOptionalBytes;
            f.set(null, fn);
            return true;
        } catch (Throwable t) {
            LOGGER.error("BytecodeBridge registration failed", t);
            return false;
        }
    }

    private static File extractAgentJar() throws IOException {
        String resourcePath = "/META-INF/jarjar/decay-agent.jar";
        InputStream is = DecayBootstrap.class.getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IOException("Embedded agent JAR not found in resources: " + resourcePath);
        }
        File tempFile = File.createTempFile("decay-agent-", ".jar");
        tempFile.deleteOnExit();
        try (FileOutputStream os = new FileOutputStream(tempFile)) {
            is.transferTo(os);
        }
        return tempFile;
    }

    private static byte[] readResource(String resource) throws IOException {
        try (InputStream in = DecayAgent.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Resource not found: " + resource);
            return in.readAllBytes();
        }
    }

    public static void verifyAndRetransform() {
        if (instrumentation == null) return;
        try {
            java.util.List<Class<?>> classesToRetransform = new java.util.ArrayList<>();
            for (Class<?> clazz : instrumentation.getAllLoadedClasses()) {
                String name = clazz.getName();
                if (name.equals("net.minecraft.world.entity.Entity") ||
                        name.equals("net.minecraft.world.entity.LivingEntity") ||
                        name.equals("net.minecraft.network.syncher.SynchedEntityData") ||
                        name.equals("net.minecraft.server.players.PlayerList") ||
                        name.equals("net.minecraft.server.level.ServerPlayer") ||
                        name.equals("net.minecraft.server.level.ServerLevel")) {
                    if (instrumentation.isModifiableClass(clazz)) {
                        classesToRetransform.add(clazz);
                    }
                }
            }
            if (!classesToRetransform.isEmpty()) {
                Class<?>[] classArray = classesToRetransform.toArray(new Class<?>[0]);
                instrumentation.retransformClasses(classArray);
                com.maxwell.hyperdamagelib.HDL.LOGGER.info("[HDL] Successfully retransformed " + classesToRetransform.size() + " target classes.");
            }
        } catch (Exception e) {
            com.maxwell.hyperdamagelib.HDL.LOGGER.error("[HDL] Failed to bulk-retransform target classes", e);
        }
    }
}