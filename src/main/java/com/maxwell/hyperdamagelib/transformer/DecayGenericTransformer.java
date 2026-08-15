package com.maxwell.hyperdamagelib.transformer;

import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DecayGenericTransformer {
    static final String ENTITY_METHODS = "com/maxwell/hyperdamagelib/transformer/DecayEntityMethods";
    public static List<String> exclusivePackages = new ArrayList<>();
    public static List<String> exclusiveInstructionWrappingPackages = new ArrayList<>();
    static boolean initialized = false;
    static boolean tickInjected = false;
    static final String ONLYIN_DESC = Type.getDescriptor(OnlyIn.class);
    static final String FML_DIST = FMLEnvironment.dist.toString();
    public static boolean availableGetBytecode = false;

    public enum Phase {
        GetBytecode, ILaunchPluginServiceBefore, ILaunchPluginService
    }

    static {
        initialize();
    }

    public static void initialize() {
        if (initialized) return;
        exclusivePackages.add("com/maxwell/hyperdamagelib/transformer");
        exclusivePackages.add("com/maxwell/hyperdamagelib/agent");
        exclusivePackages.add("com/maxwell/hyperdamagelib/shadow");
        initialized = true;
    }

    public static boolean transform(Phase phase, ClassNode classNode) {
        if (exclusivePackages.stream().anyMatch(packageName -> classNode.name.startsWith(packageName)))
            return false;
        boolean modified = false;

        boolean shouldWrapInsn = (phase == Phase.GetBytecode || phase == Phase.ILaunchPluginServiceBefore)
                && exclusiveInstructionWrappingPackages.stream().noneMatch(packageName -> classNode.name.startsWith(packageName));
        boolean shouldModifyReturn = (phase == Phase.GetBytecode || phase == Phase.ILaunchPluginService);

        // 1. 各メソッド内のCall-site（呼び出し元）の書き換え
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof MethodInsnNode methodInsn && shouldWrapInsn) {
                    if (insn.getOpcode() == Opcodes.INVOKEVIRTUAL || insn.getOpcode() == Opcodes.INVOKEINTERFACE) {
                        if (isSameMethod(methodInsn.owner, methodInsn, "net/minecraft/world/entity/LivingEntity", "m_21223_", "getHealth", "()F", false)) {
                            method.instructions.insertBefore(methodInsn, new InsnNode(Opcodes.DUP));
                            InsnList insnList = new InsnList();
                            insnList.add(new InsnNode(Opcodes.SWAP));
                            insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "getHealth", "(FLnet/minecraft/world/entity/LivingEntity;)F", false));
                            method.instructions.insert(methodInsn, insnList);
                            method.maxStack += 1;
                            modified = true;
                        } else if (isSameMethod(methodInsn.owner, methodInsn, "net/minecraft/world/entity/LivingEntity", "m_21224_", "isDeadOrDying", "()Z", false)) {
                            method.instructions.insertBefore(methodInsn, new InsnNode(Opcodes.DUP));
                            InsnList insnList = new InsnList();
                            insnList.add(new InsnNode(Opcodes.SWAP));
                            insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "isDeadOrDying", "(ZLnet/minecraft/world/entity/LivingEntity;)Z", false));
                            method.instructions.insert(methodInsn, insnList);
                            method.maxStack += 1;
                            modified = true;
                        } else if (isSameMethod(methodInsn.owner, methodInsn, "net/minecraft/world/entity/Entity", "m_6084_", "isAlive", "()Z", false)) {
                            method.instructions.insertBefore(methodInsn, new InsnNode(Opcodes.DUP));
                            InsnList insnList = new InsnList();
                            insnList.add(new InsnNode(Opcodes.SWAP));
                            insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "isAlive", "(ZLnet/minecraft/world/entity/Entity;)Z", false));
                            method.instructions.insert(methodInsn, insnList);
                            method.maxStack += 1;
                            modified = true;
                        } else if (isSameMethod(methodInsn.owner, methodInsn, "net/minecraft/world/entity/Entity", "m_240725_", "isRemoved", "()Z", false)) {
                            method.instructions.insertBefore(methodInsn, new InsnNode(Opcodes.DUP));
                            InsnList insnList = new InsnList();
                            insnList.add(new InsnNode(Opcodes.SWAP));
                            insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "isRemoved", "(ZLnet/minecraft/world/entity/Entity;)Z", false));
                            method.instructions.insert(methodInsn, insnList);
                            method.maxStack += 1;
                            modified = true;
                        } else if (isSameMethod(methodInsn.owner, methodInsn, "net/minecraft/world/entity/Entity", "m_146911_", "getRemovalReason", "()Lnet/minecraft/world/entity/Entity$RemovalReason;", false)) {
                            method.instructions.insertBefore(methodInsn, new InsnNode(Opcodes.DUP));
                            InsnList insnList = new InsnList();
                            insnList.add(new InsnNode(Opcodes.SWAP));
                            insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "getRemovalReason", "(Lnet/minecraft/world/entity/Entity$RemovalReason;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/entity/Entity$RemovalReason;", false));
                            method.instructions.insert(methodInsn, insnList);
                            method.maxStack += 1;
                            modified = true;
                        } else if (isSameMethod(classNode.name, method, "net/minecraft/world/level/entity/EntityTickList", "m_156910_", "forEach", "(Ljava/util/function/Consumer;)V", false) &&
                                isSameMethod(methodInsn.owner, methodInsn, "java/util/function/Consumer", "accept", "accept", "(Ljava/lang/Object;)V", true)) {
                            LabelNode skipLabelNode = new LabelNode(new Label());
                            LabelNode endLabelNode = new LabelNode(new Label());
                            InsnList insnListB = new InsnList();
                            insnListB.add(new InsnNode(Opcodes.DUP));
                            insnListB.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "shouldOverrideTick", "(Lnet/minecraft/world/entity/Entity;)Z", false));
                            insnListB.add(new JumpInsnNode(Opcodes.IFGT, skipLabelNode));
                            InsnList insnListA = new InsnList();
                            insnListA.add(new JumpInsnNode(Opcodes.GOTO, endLabelNode));
                            insnListA.add(skipLabelNode);
                            insnListA.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "tickOverride", "(Ljava/util/function/Consumer;Lnet/minecraft/world/entity/Entity;)V", false));
                            insnListA.add(endLabelNode);
                            insnListA.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
                            method.instructions.insertBefore(methodInsn, insnListB);
                            method.instructions.insert(methodInsn, insnListA);
                            method.maxStack += 1;
                            modified = true;
                        }
                    }
                } else if (shouldModifyReturn) {
                    if (insn.getOpcode() == Opcodes.FRETURN) {
                        if (isSameMethod(classNode.name, method, "net/minecraft/world/entity/LivingEntity", "m_21223_", "getHealth", "()F", false)) {
                            InsnList insnList = new InsnList();
                            insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "getHealth", "(FLnet/minecraft/world/entity/LivingEntity;)F", false));
                            method.instructions.insertBefore(insn, insnList);
                            method.maxStack += 1;
                            modified = true;
                        }
                    } else if (insn.getOpcode() == Opcodes.IRETURN) {
                        if (isSameMethod(classNode.name, method, "net/minecraft/world/entity/LivingEntity", "m_21224_", "isDeadOrDying", "()Z", false)) {
                            InsnList insnList = new InsnList();
                            insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "isDeadOrDying", "(ZLnet/minecraft/world/entity/LivingEntity;)Z", false));
                            method.instructions.insertBefore(insn, insnList);
                            method.maxStack += 1;
                            modified = true;
                        } else if (isSameMethod(classNode.name, method, "net/minecraft/world/entity/Entity", "m_6084_", "isAlive", "()Z", false)) {
                            InsnList insnList = new InsnList();
                            insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "isAlive", "(ZLnet/minecraft/world/entity/Entity;)Z", false));
                            method.instructions.insertBefore(insn, insnList);
                            method.maxStack += 1;
                            modified = true;
                        } else if (isSameMethod(classNode.name, method, "net/minecraft/world/entity/Entity", "m_213877_", "isRemoved", "()Z", false)) {
                            InsnList insnList = new InsnList();
                            insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "isRemoved", "(ZLnet/minecraft/world/entity/Entity;)Z", false));
                            method.instructions.insertBefore(insn, insnList);
                            method.maxStack += 1;
                            modified = true;
                        }
                    } else if (insn.getOpcode() == Opcodes.ARETURN) {
                        if (isSameMethod(classNode.name, method, "net/minecraft/world/entity/Entity", "m_146911_", "getRemovalReason", "()Lnet/minecraft/world/entity/Entity$RemovalReason;", false)) {
                            InsnList insnList = new InsnList();
                            insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "getRemovalReason", "(Lnet/minecraft/world/entity/Entity$RemovalReason;Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/entity/Entity$RemovalReason;", false));
                            method.instructions.insertBefore(insn, insnList);
                            method.maxStack += 1;
                            modified = true;
                        }
                    }
                }
            }

            // 2. メソッド先頭への門番（injectHead）注入
            if (shouldModifyReturn) {
                if (isSameMethod(classNode.name, method, "net/minecraft/world/entity/LivingEntity", "m_21223_", "getHealth", "()F", false)) {
                    injectHead(method,
                            new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "shouldReplaceHealthMethod", "(Lnet/minecraft/world/entity/Entity;)Z", false),
                            new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "replaceGetHealth", "(Lnet/minecraft/world/entity/LivingEntity;)F", false),
                            new InsnNode(Opcodes.FRETURN));
                    method.maxStack += 1;
                    modified = true;
                } else if (isSameMethod(classNode.name, method, "net/minecraft/world/entity/LivingEntity", "m_21224_", "isDeadOrDying", "()Z", false)) {
                    injectHead(method,
                            new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "shouldReplaceHealthMethod", "(Lnet/minecraft/world/entity/Entity;)Z", false),
                            new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "replaceIsDeadOrDying", "(Lnet/minecraft/world/entity/Entity;)Z", false),
                            new InsnNode(Opcodes.IRETURN));
                    method.maxStack += 1;
                    modified = true;
                } else if (isSameMethod(classNode.name, method, "net/minecraft/world/entity/Entity", "m_6084_", "isAlive", "()Z", false)) {
                    injectHead(method,
                            new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "shouldReplaceHealthMethod", "(Lnet/minecraft/world/entity/Entity;)Z", false),
                            new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "replaceIsAlive", "(Lnet/minecraft/world/entity/Entity;)Z", false),
                            new InsnNode(Opcodes.IRETURN));
                    method.maxStack += 1;
                    modified = true;
                } else if (isSameMethod(classNode.name, method, "net/minecraft/world/entity/Entity", "m_6087_", "isPickable", "()Z", false)) {
                    injectHead(method,
                            new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "shouldReplaceIsPickable", "(Lnet/minecraft/world/entity/Entity;)Z", false),
                            new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "replaceIsPickable", "(Lnet/minecraft/world/entity/Entity;)Z", false),
                            new InsnNode(Opcodes.IRETURN));
                    method.maxStack += 1;
                    modified = true;
                }
            }
        }
        return modified;
    }

    public static void injectHead(MethodNode method, MethodInsnNode judgeMethod, MethodInsnNode replaceMethod, InsnNode returnInsn) {
        LabelNode skipLabelNode = new LabelNode(new Label());
        InsnList insnList = new InsnList();
        insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insnList.add(judgeMethod);
        insnList.add(new JumpInsnNode(Opcodes.IFLE, skipLabelNode));
        insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insnList.add(replaceMethod);
        insnList.add(returnInsn);
        insnList.add(skipLabelNode);
        insnList.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        method.instructions.insertBefore(method.instructions.getFirst(), insnList);
    }

    public static boolean isSameMethod(String owner, MethodInsnNode methodInsn, String superClass, String obfName, String name, String desc, boolean isInterface) {
        if ((!obfName.equals(methodInsn.name) && !name.equals(methodInsn.name)) || !desc.equals(methodInsn.desc)) {
            return false;
        }
        return isSubclass(owner, superClass, isInterface);
    }

    public static boolean isSameMethod(String owner, MethodNode method, String superClass, String obfName, String name, String desc, boolean isInterface) {
        if ((!obfName.equals(method.name) && !name.equals(method.name)) || !desc.equals(method.desc)) {
            return false;
        }
        return isSubclass(owner, superClass, isInterface);
    }

    public static boolean isSubclass(String className, String superClass, boolean isInterface) {
        if (className == null || superClass == null) return false;
        if (className.equals(superClass) || superClass.equals("java/lang/Object")) return true;
        if (className.equals("java/lang/Object")) return false;

        String currentName = className.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) classLoader = DecayGenericTransformer.class.getClassLoader();

        int maxDepth = 30;
        while (currentName != null && !currentName.equals("java/lang/Object") && maxDepth-- > 0) {
            try (InputStream is = classLoader.getResourceAsStream(currentName.concat(".class"))) {
                if (is == null) return false;
                ClassReader classReader = new ClassReader(is);
                currentName = classReader.getSuperName();
                ClassNode classNode = new ClassNode(Opcodes.ASM9);
                classReader.accept(classNode, ClassReader.SKIP_CODE);
                if (classNode.visibleAnnotations != null && classNode.visibleAnnotations.stream().anyMatch(annotationNode -> annotationNode.desc.equals(ONLYIN_DESC) && !((String[]) annotationNode.values.get(annotationNode.values.indexOf("value") + 1))[1].equals(FML_DIST))) {
                    return false;
                }
                if (currentName != null && currentName.equals(superClass)) {
                    return true;
                }
                if (isInterface) {
                    for (String interfaceName : classReader.getInterfaces()) {
                        if (isSubclass(interfaceName, superClass, true)) {
                            return true;
                        }
                    }
                }
            } catch (Throwable e) {
                return false;
            }
        }
        return false;
    }
}