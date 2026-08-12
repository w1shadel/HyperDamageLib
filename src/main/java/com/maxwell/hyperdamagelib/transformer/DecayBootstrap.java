package com.maxwell.hyperdamagelib.transformer;

import com.maxwell.hyperdamagelib.HDL;
import com.maxwell.hyperdamagelib.agent.DecayBytecodeBridge;
import net.bytebuddy.agent.ByteBuddyAgent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.jar.JarFile;

public final class DecayBootstrap {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("DecayBootstrap");
    private static final String BRIDGE_CLASS = "com.maxwell.hyperdamagelib.agent.DecayBytecodeBridge";
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
        try {
            LOGGER.info("[HDL] Starting secure JVM instrumentation bootstrap...");
            ByteBuddyAgent.install();
            instrumentation = ByteBuddyAgent.getInstrumentation();
            if (instrumentation != null) {
                LOGGER.info("[HDL] Instrumentation successfully acquired via ByteBuddy.");
                File agentJar = extractAgentJar();
                JarFile jarFile = new JarFile(agentJar);
                instrumentation.appendToBootstrapClassLoaderSearch(jarFile);
                LOGGER.info("[HDL] Successfully appended decay-agent.jar to Bootstrap class loader search path.");
                registerBridge();
                instrumentation.addTransformer(new DecayBytecodeGetterTransformer(), true);
                try {
                    Class<?> classLoaderClass = Class.forName("cpw.mods.cl.ModuleClassLoader");
                    instrumentation.retransformClasses(classLoaderClass);
                    LOGGER.info("[HDL] ModuleClassLoader successfully hacked & retransformed.");
                } catch (ClassNotFoundException e) {
                    LOGGER.error("[HDL] ModuleClassLoader class not found! Hook failed.", e);
                }

            } else {
                LOGGER.error("[HDL] ByteBuddy returned null Instrumentation!");
            }
        } catch (Throwable t) {
            LOGGER.error("[HDL] Critical error during DecayBootstrap.start()", t);
        }
    }

    private static File extractAgentJar() throws IOException {
        String resourcePath = "/META-INF/jarjar/decay-agent.jar";
        InputStream is = DecayBootstrap.class.getResourceAsStream(resourcePath);
        if (is == null) {
            throw new IOException("Embedded agent JAR not found in resources: " + resourcePath);
        }
        java.nio.file.Path tempPath;
        try {
            java.nio.file.attribute.FileAttribute<?>[] attrs = new java.nio.file.attribute.FileAttribute<?>[0];
            if (java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                java.util.Set<java.nio.file.attribute.PosixFilePermission> perms =
                        java.nio.file.attribute.PosixFilePermissions.fromString("rw-------");
                attrs = new java.nio.file.attribute.FileAttribute<?>[]{
                        java.nio.file.attribute.PosixFilePermissions.asFileAttribute(perms)
                };
            }
            tempPath = java.nio.file.Files.createTempFile("decay-agent-", ".jar", attrs);
        } catch (UnsupportedOperationException e) {
            tempPath = java.nio.file.Files.createTempFile("decay-agent-", ".jar");
        }
        File tempFile = tempPath.toFile();
        tempFile.deleteOnExit();
        tempFile.setReadable(true, true);
        tempFile.setWritable(true, true);
        tempFile.setExecutable(false, false);
        try (FileOutputStream os = new FileOutputStream(tempFile)) {
            is.transferTo(os);
        }
        return tempFile;
    }

    private static boolean registerBridge() {
        try {
            BiFunction<Optional<byte[]>, String, Optional<byte[]>> fn = DecayBytecodeGetterTransformer::transformOptionalBytes;
            DecayBytecodeBridge.setTransformer(fn);
            return true;
        } catch (Throwable t) {
            LOGGER.error("BytecodeBridge registration failed", t);
            return false;
        }
    }

    public static void verifyAndRetransform() {
        if (instrumentation == null) {
            LOGGER.warn("[HDL] Instrumentation is null, skipping class retransformation.");
            return;
        }
        try {
            List<Class<?>> classesToRetransform = new ArrayList<>();
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
                HDL.LOGGER.info("[HDL] Successfully retransformed " + classesToRetransform.size() + " target classes.");
            }
        } catch (Exception e) {
            HDL.LOGGER.error("[HDL] Failed to bulk-retransform target classes", e);
        }
    }
}