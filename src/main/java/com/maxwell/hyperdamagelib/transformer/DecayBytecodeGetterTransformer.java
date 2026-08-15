package com.maxwell.hyperdamagelib.transformer;

import com.maxwell.hyperdamagelib.HDL;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Optional;
import java.util.stream.Stream;

public class DecayBytecodeGetterTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(Module module, ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (!"cpw/mods/cl/ModuleClassLoader".equals(className)) return null;
        HDL.LOGGER.info("[HDL] Intercepting ModuleClassLoader: " + className);

        try {
            byte[] result = transformClass(classfileBuffer, loader);
            if (result != null && result != classfileBuffer) {
                return result;
            }
        } catch (Exception e) {
            HDL.LOGGER.error("[HDL] Transform ModuleClassLoader failed", e);
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
                        for (AbstractInsnNode insnNode : mn.instructions) {
                            if (insnNode instanceof MethodInsnNode methodInsn && methodInsn.name.equals("findFirst")) {
                                InsnList insnList = new InsnList();
                                insnList.add(new VarInsnNode(Opcodes.ALOAD, 3));
                                insnList.add(new MethodInsnNode(
                                        Opcodes.INVOKESTATIC,
                                        "com/maxwell/hyperdamagelib/agent/DecayBytecodeBridge",
                                        "transformStreamBytes",
                                        "(Ljava/util/stream/Stream;Ljava/lang/String;)Ljava/util/stream/Stream;"
                                ));
                                mn.instructions.insertBefore(insnNode, insnList);
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
            HDL.LOGGER.error("[HDL] ModuleClassLoader transform error: " + t.getMessage(), t);
            return bytes;
        }
    }

    public static Stream<byte[]> transformStreamBytes(Stream<byte[]> stream, String className) {
        if (stream == null || DecayGenericTransformer.exclusivePackages.stream().anyMatch(className::startsWith)) {
            return stream;
        }
        return stream.map(bytes -> transformBytes(bytes, className));
    }

    public static Optional<byte[]> transformOptionalBytes(Optional<byte[]> optionalBytes, String className) {
        if (optionalBytes.isEmpty() || DecayGenericTransformer.exclusivePackages.stream().anyMatch(className::startsWith)) {
            return optionalBytes;
        }
        return Optional.of(transformBytes(optionalBytes.orElse(new byte[0]), className));
    }

    private static byte[] transformBytes(byte[] bytes, String className) {
        if (bytes == null || bytes.length == 0) return bytes;

        ClassNode classNode;
        boolean modified;
        try {
            ClassReader classReader = new ClassReader(bytes);
            classNode = new ClassNode(Opcodes.ASM9);
            classReader.accept(classNode, ClassReader.EXPAND_FRAMES);
            modified = DecayGenericTransformer.transform(DecayGenericTransformer.Phase.GetBytecode, classNode);
        } catch (Throwable t) {
            HDL.LOGGER.error("[HDL] transformBytes: read/transform failed for {}", className, t);
            return bytes;
        }

        if (!modified) return bytes;

        try {
            ClassWriter cw = new HierarchyAwareClassWriter(classNode, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            classNode.accept(cw);
            return cw.toByteArray();
        } catch (Throwable t) {
            HDL.LOGGER.error("[HDL] transformBytes: write failed for {} -- falling back to original bytes", className, t);
            return bytes;
        }
    }
}