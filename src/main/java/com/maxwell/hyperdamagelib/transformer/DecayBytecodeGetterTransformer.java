package com.maxwell.hyperdamagelib.transformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class DecayBytecodeGetterTransformer implements ClassFileTransformer {
    private static final Logger LOGGER = LoggerFactory.getLogger("DecayBytecodeGetterTransformer");

    public static Optional<byte[]> transformOptionalBytes(Optional<byte[]> optionalBytes, String className) {
        if (optionalBytes.isEmpty() || DecayGenericTransformer.exclusivePackages.stream().anyMatch(className::startsWith)) {
            return optionalBytes;
        }
        byte[] bytes = optionalBytes.orElse(new byte[0]);
        ClassNode classNode;
        boolean modified;
        try {
            ClassReader classReader = new ClassReader(bytes);
            classNode = new ClassNode(Opcodes.ASM9);
            classReader.accept(classNode, ClassReader.EXPAND_FRAMES);
            modified = DecayGenericTransformer.transform(DecayGenericTransformer.Phase.GetBytecode, classNode);
        } catch (Throwable t) {
            LOGGER.error("transformOptionalBytes: read/transform failed for {} ({}): {}", className, t.getClass().getName(), t.getMessage(), t);
            return optionalBytes;
        }
        if (!modified) return optionalBytes;
        try {
            ClassWriter cw = new HierarchyAwareClassWriter(classNode, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            classNode.accept(cw);
            return Optional.of(cw.toByteArray());
        } catch (Throwable t) {
            LOGGER.error("transformOptionalBytes: write failed for {} ({}): {} -- falling back to original bytes", className, t.getClass().getName(), t.getMessage(), t);
            return optionalBytes;
        }
    }

    @Override
    public byte[] transform(Module module, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (!"cpw/mods/cl/ModuleClassLoader".equals(className)) return null;
        LOGGER.info("Found Target ClassLoader: " + className);
        try {
            byte[] result = transformClass(classfileBuffer, loader);
            if (result != null && result != classfileBuffer) {
                return result;
            }
        } catch (Exception e) {
            LOGGER.error("Transform ClassLoader failed", e);
            return null;
        }
        return null;
    }

    private byte[] transformClass(byte[] bytes, ClassLoader loader) {
        if (bytes == null || bytes.length == 0) return bytes;
        try {
            ClassReader cr = new ClassReader(bytes);
            ClassNode cn = new ClassNode(Opcodes.ASM9);
            cr.accept(cn, ClassReader.EXPAND_FRAMES);
            boolean modified = false;
            if (cn.methods != null) {
                for (MethodNode mn : cn.methods) {
                    if ("getClassBytes".equals(mn.name) &&
                            "(Ljava/lang/module/ModuleReader;Ljava/lang/module/ModuleReference;Ljava/lang/String;)[B".equals(mn.desc)) {
                        LOGGER.info("Modifying ModuleClassLoader.getClassBytes to inject BytecodeBridge...");
                        for (AbstractInsnNode insnNode : mn.instructions) {
                            if (insnNode instanceof MethodInsnNode methodInsn && methodInsn.name.equals("findFirst")) {
                                InsnList insnList = new InsnList();
                                insnList.add(new VarInsnNode(Opcodes.ALOAD, 3));
                                insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "com/maxwell/hyperdamagelib/agent/DecayBytecodeBridge", "transformOptionalBytes", "(Ljava/util/Optional;Ljava/lang/String;)Ljava/util/Optional;"));
                                mn.instructions.insert(insnNode, insnList);
                                mn.maxStack += 1;
                                DecayGenericTransformer.availableGetBytecode = true;
                                break;
                            }
                        }
                        modified = true;
                        break;
                    }
                }
            }
            if (!modified) return bytes;
            ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            return cw.toByteArray();

        } catch (Throwable t) {
            LOGGER.error("ModuleClassLoader transform failed: " + t.getMessage(), t);
            return bytes;
        }
    }

    private static class HierarchyAwareClassWriter extends ClassWriter {
        private final ClassLoader loader;
        private final ClassNode currentClass;

        HierarchyAwareClassWriter(ClassNode classNode, int flags) {
            super(flags);
            this.currentClass = classNode;
            this.loader = Thread.currentThread().getContextClassLoader();
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            try {
                if (type1.equals(type2)) return type1;
                if ("java/lang/Object".equals(type1) || "java/lang/Object".equals(type2)) return "java/lang/Object";
                Set<String> ancestors = new HashSet<>();
                String c = type1;
                while (c != null) {
                    ancestors.add(c);
                    if ("java/lang/Object".equals(c)) break;
                    c = readSuperName(c);
                }
                ancestors.add("java/lang/Object");
                c = type2;
                while (c != null) {
                    if (ancestors.contains(c)) return c;
                    if ("java/lang/Object".equals(c)) break;
                    c = readSuperName(c);
                }
                return "java/lang/Object";
            } catch (Throwable t) {
                LOGGER.warn("getCommonSuperClass({}, {}) failed -- defaulting to Object", type1, type2);
                return "java/lang/Object";
            }
        }

        private String readSuperName(String name) throws IOException {
            if (currentClass != null && name.equals(currentClass.name)) {
                return currentClass.superName != null ? currentClass.superName : "java/lang/Object";
            }
            try (InputStream is = loader.getResourceAsStream(name + ".class")) {
                if (is == null) return "java/lang/Object";
                String s = new ClassReader(is).getSuperName();
                return s != null ? s : "java/lang/Object";
            }
        }
    }
}