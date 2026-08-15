package com.maxwell.hyperdamagelib.transformer;

import com.maxwell.hyperdamagelib.HDL;
import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import net.bytebuddy.agent.ByteBuddyAgent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public final class DecayBootstrap {
    private static final String BRIDGE_CLASS = "com.maxwell.hyperdamagelib.agent.DecayBytecodeBridge";
    public static volatile Instrumentation instrumentation = null;
    public static volatile boolean LAUNCH_PLUGIN_AVAILABLE = false;
    private static volatile boolean STARTED = false;

    private DecayBootstrap() {}

    public static void start() {
        if (STARTED) return;
        synchronized (DecayBootstrap.class) {
            if (STARTED) return;
            STARTED = true;
        }
        try {
            // 1. ModLauncher への LaunchPlugin 直接注入（Mixin適用後のコードを捕まえる最重要フック）
            if (!LAUNCH_PLUGIN_AVAILABLE) {
                LAUNCH_PLUGIN_AVAILABLE = initLaunchPlugin();
            }

            // 2. エージェントの起動とクラスローダーのフック
            if (instrumentation == null) {
                ByteBuddyAgent.install();
                instrumentation = ByteBuddyAgent.getInstrumentation();
                if (instrumentation != null) {
                    File agentJar = extractAgentJar();
                    JarFile jarFile = new JarFile(agentJar);
                    instrumentation.appendToBootstrapClassLoaderSearch(jarFile);
                    registerBridge();

                    instrumentation.addTransformer(new DecayBytecodeGetterTransformer(), true);
                    Class<?> classLoaderClass = Class.forName("cpw.mods.cl.ModuleClassLoader");
                    instrumentation.retransformClasses(classLoaderClass);

                    // 3. ネイティブトランスフォーマーの追加
                    instrumentation.addTransformer(new GenericClassFileTransformer(), true);
                    HDL.LOGGER.info("[HDL] Triple-layer bytecode transformation pipeline active.");
                }
            }
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
            HDL.LOGGER.error("[HDL] Failed to inject LaunchPlugin", e);
            return false;
        }
    }

    private static File extractAgentJar() throws IOException {
        String resourcePath = "/META-INF/jarjar/decay-agent.jar";
        InputStream is = DecayBootstrap.class.getResourceAsStream(resourcePath);
        if (is == null) throw new IOException("Agent JAR not found: " + resourcePath);
        java.nio.file.Path tempPath = java.nio.file.Files.createTempFile("decay-agent-", ".jar");
        File tempFile = tempPath.toFile();
        tempFile.deleteOnExit();
        try (FileOutputStream os = new FileOutputStream(tempFile)) {
            is.transferTo(os);
        }
        return tempFile;
    }

    private static boolean registerBridge() {
        try {
            Class<?> bridgeCls = Class.forName(BRIDGE_CLASS, true, ClassLoader.getSystemClassLoader());
            Field streamField = bridgeCls.getField("streamTransformer");
            BiFunction<Stream<byte[]>, String, Stream<byte[]>> streamFn = DecayBytecodeGetterTransformer::transformStreamBytes;
            streamField.set(null, streamFn);

            Field optField = bridgeCls.getField("transformer");
            BiFunction<Optional<byte[]>, String, Optional<byte[]>> optFn = DecayBytecodeGetterTransformer::transformOptionalBytes;
            optField.set(null, optFn);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}