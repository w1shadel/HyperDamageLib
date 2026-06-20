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
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

public final class DecayBootstrap {
    private static final Logger LOGGER = LoggerFactory.getLogger("DecayBootstrap");
    private static final String AGENT_CLASS = "com.maxwell.hyperdamagelib.agent.DecayAgent";
    private static final String AGENT_RESOURCE = "com/maxwell/hyperdamagelib/agent/DecayAgent.class";
    private static final String BRIDGE_CLASS = "com.maxwell.hyperdamagelib.agent.DecayBytecodeBridge";
    private static final String BRIDGE_RESOURCE = "com/maxwell/hyperdamagelib/agent/DecayBytecodeBridge.class";
    public static volatile Instrumentation instrumentation = null;
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
            File agentJar = buildAgentJar();
            LOGGER.debug("Agent jar: {}", agentJar.getAbsolutePath());
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

    private static File buildAgentJar() throws IOException {
        byte[] agentBytes = readResource(AGENT_RESOURCE);
        byte[] bridgeBytes = readResource(BRIDGE_RESOURCE);
        Manifest mf = new Manifest();
        Attributes a = mf.getMainAttributes();
        a.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        a.putValue("Agent-Class", AGENT_CLASS);
        a.putValue("Premain-Class", AGENT_CLASS);
        a.putValue("Can-Retransform-Classes", "true");
        a.putValue("Can-Redefine-Classes", "true");
        File jar = File.createTempFile("decay/decay-agent-", ".jar");
        jar.deleteOnExit();
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar), mf)) {
            jos.putNextEntry(new JarEntry(AGENT_RESOURCE));
            jos.write(agentBytes);
            jos.closeEntry();
            jos.putNextEntry(new JarEntry(BRIDGE_RESOURCE));
            jos.write(bridgeBytes);
            jos.closeEntry();
        }
        return jar;
    }

    private static byte[] readResource(String resource) throws IOException {
        try (InputStream in = DecayAgent.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Resource not found: " + resource);
            return in.readAllBytes();
        }
    }
}