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
    static final String ONLYIN_DESC = Type.getDescriptor(OnlyIn.class);
    static final String FML_DIST = FMLEnvironment.dist.toString();
    public static List<String> exclusivePackages = new ArrayList<>();
    static boolean initialized = false;
    static boolean availableGetBytecode = false;
    static boolean tickInjected = false;

    static {
        initialize();
    }

    public static void initialize() {
        if (initialized) return;
        exclusivePackages.add("com/maxwell/hyperdamagelib/transformer");
        exclusivePackages.add("com/maxwell/hyperdamagelib/agent");
        initialized = true;
    }

    public static boolean transform(Phase phase, ClassNode classNode) {
        if (exclusivePackages.stream().anyMatch(packageName -> classNode.name.startsWith(packageName)))
            return false;
        boolean modified = false;
        if (classNode.name.equals("net/minecraft/world/entity/Entity")) {
            for (MethodNode method : classNode.methods) {
                if ((method.name.equals("kill") || method.name.equals("m_6074_")) &&
                        method.desc.equals("()V")) {
                    InsnList insnList = new InsnList();
                    insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    insnList.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            ENTITY_METHODS,
                            "handleForceKill",
                            "(Lnet/minecraft/world/entity/Entity;)Z",
                            false
                    ));
                    LabelNode label = new LabelNode();
                    insnList.add(new JumpInsnNode(Opcodes.IFEQ, label));
                    insnList.add(new InsnNode(Opcodes.RETURN));
                    insnList.add(label);
                    method.instructions.insertBefore(method.instructions.getFirst(), insnList);
                    method.maxStack = Math.max(method.maxStack, 1);
                    modified = true;
                }
            }
        }
        if (classNode.name.equals("net/minecraft/world/entity/LivingEntity") ||
                isSubclass(classNode.name, "net/minecraft/world/entity/LivingEntity", false)) {

            for (MethodNode method : classNode.methods) {

                if ((method.name.equals("die") || method.name.equals("m_6667_")) &&
                        method.desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;)V")) {
                    InsnList insnList = new InsnList();
                    insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    insnList.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    insnList.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            ENTITY_METHODS,
                            "handleForceDie",
                            "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;)Z",
                            false
                    ));
                    LabelNode label = new LabelNode();
                    insnList.add(new JumpInsnNode(Opcodes.IFEQ, label));
                    insnList.add(new InsnNode(Opcodes.RETURN)); 
                    insnList.add(label);
                    method.instructions.insertBefore(method.instructions.getFirst(), insnList);
                    method.maxStack = Math.max(method.maxStack, 2);
                    modified = true;
                }

                if ((method.name.equals("hurt") || method.name.equals("m_6469_")) &&
                        method.desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;F)Z")) {
                    InsnList insnList = new InsnList();
                    insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    insnList.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    insnList.add(new VarInsnNode(Opcodes.FLOAD, 2));
                    insnList.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            ENTITY_METHODS,
                            "handleForceDamage",
                            "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                            false
                    ));
                    LabelNode label = new LabelNode();
                    insnList.add(new JumpInsnNode(Opcodes.IFEQ, label));
                    insnList.add(new InsnNode(Opcodes.ICONST_1)); 
                    insnList.add(new InsnNode(Opcodes.IRETURN));
                    insnList.add(label);
                    method.instructions.insertBefore(method.instructions.getFirst(), insnList);
                    method.maxStack = Math.max(method.maxStack, 3);
                    modified = true;
                }

                if ((method.name.equals("actuallyHurt") || method.name.equals("m_6475_")) &&
                        method.desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;F)V")) {
                    InsnList insnList = new InsnList();
                    insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    insnList.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    insnList.add(new VarInsnNode(Opcodes.FLOAD, 2));
                    insnList.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            ENTITY_METHODS,
                            "handleForceActuallyHurt",
                            "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
                            false
                    ));
                    LabelNode label = new LabelNode();
                    insnList.add(new JumpInsnNode(Opcodes.IFEQ, label));
                    insnList.add(new InsnNode(Opcodes.RETURN)); 
                    insnList.add(label);
                    method.instructions.insertBefore(method.instructions.getFirst(), insnList);
                    method.maxStack = Math.max(method.maxStack, 3);
                    modified = true;
                }

                if ((method.name.equals("setHealth") || method.name.equals("m_21153_")) &&
                        method.desc.equals("(F)V")) {
                    InsnList insnList = new InsnList();
                    insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    insnList.add(new VarInsnNode(Opcodes.FLOAD, 1));
                    insnList.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            ENTITY_METHODS,
                            "handleSetHealth",
                            "(Lnet/minecraft/world/entity/LivingEntity;F)F",
                            false
                    ));
                    insnList.add(new VarInsnNode(Opcodes.FSTORE, 1)); 
                    method.instructions.insertBefore(method.instructions.getFirst(), insnList);
                    method.maxStack = Math.max(method.maxStack, 2);
                    modified = true;
                }
            }
        }
        if (classNode.name.equals("net/minecraft/server/players/PlayerList")) {
            for (MethodNode method : classNode.methods) {
                if ((method.name.equals("respawn") || method.name.equals("m_11236_")) &&
                        method.desc.equals("(Lnet/minecraft/server/level/ServerPlayer;Z)Lnet/minecraft/server/level/ServerPlayer;")) {
                    InsnList insnList = new InsnList();
                    insnList.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    insnList.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            ENTITY_METHODS,
                            "shouldPreventRespawn",
                            "(Lnet/minecraft/world/entity/Entity;)Z",
                            false
                    ));
                    LabelNode label = new LabelNode();
                    insnList.add(new JumpInsnNode(Opcodes.IFEQ, label));
                    insnList.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    insnList.add(new InsnNode(Opcodes.ARETURN));
                    insnList.add(label);
                    method.instructions.insertBefore(method.instructions.getFirst(), insnList);
                    method.maxStack = Math.max(method.maxStack, 2);
                    modified = true;
                }
            }
        }
        if (classNode.name.equals("net/minecraft/server/level/ServerPlayer")) {
            for (MethodNode method : classNode.methods) {
                if ((method.name.equals("die") || method.name.equals("m_6667_")) &&
                        method.desc.equals("(Lnet/minecraft/world/damagesource/DamageSource;)V")) {
                    InsnList insnList = new InsnList();
                    insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    insnList.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            ENTITY_METHODS,
                            "shouldPreventServerPlayerDie",
                            "(Lnet/minecraft/world/entity/Entity;)Z",
                            false
                    ));
                    LabelNode label = new LabelNode();
                    insnList.add(new JumpInsnNode(Opcodes.IFEQ, label));
                    insnList.add(new InsnNode(Opcodes.RETURN));
                    insnList.add(label);
                    method.instructions.insertBefore(method.instructions.getFirst(), insnList);
                    method.maxStack = Math.max(method.maxStack, 1);
                    modified = true;
                }
                if ((method.name.equals("teleportTo") || method.name.equals("m_8999_")) &&
                        method.desc.equals("(Lnet/minecraft/server/level/ServerLevel;DDDFF)V")) {
                    InsnList insnList = new InsnList();
                    insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    insnList.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            ENTITY_METHODS,
                            "shouldPreventTeleport",
                            "(Lnet/minecraft/world/entity/Entity;)Z",
                            false
                    ));
                    LabelNode label = new LabelNode();
                    insnList.add(new JumpInsnNode(Opcodes.IFEQ, label));
                    insnList.add(new InsnNode(Opcodes.RETURN));
                    insnList.add(label);
                    method.instructions.insertBefore(method.instructions.getFirst(), insnList);
                    method.maxStack = Math.max(method.maxStack, 2);
                    modified = true;
                }
            }
        }
        if (classNode.name.equals("net/minecraft/network/syncher/SynchedEntityData")) {
            for (MethodNode method : classNode.methods) {
                if ((method.name.equals("set") || method.name.equals("m_135381_"))
                        && method.desc.equals("(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)V")) {
                    InsnList insnList = new InsnList();
                    insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    insnList.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    insnList.add(new VarInsnNode(Opcodes.ALOAD, 2));
                    insnList.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/maxwell/hyperdamagelib/transformer/DecaySynchedEntityDataMethods",
                            "handleForceSet",
                            "(Lnet/minecraft/network/syncher/SynchedEntityData;Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)Z",
                            false
                    ));
                    LabelNode label = new LabelNode();
                    insnList.add(new JumpInsnNode(Opcodes.IFEQ, label));
                    insnList.add(new InsnNode(Opcodes.RETURN));
                    insnList.add(label);
                    insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                    insnList.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    insnList.add(new VarInsnNode(Opcodes.ALOAD, 2));
                    insnList.add(new MethodInsnNode(
                            Opcodes.INVOKESTATIC,
                            "com/maxwell/hyperdamagelib/transformer/DecaySynchedEntityDataMethods",
                            "onSet",
                            "(Lnet/minecraft/network/syncher/SynchedEntityData;Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)Ljava/lang/Object;",
                            false
                    ));
                    insnList.add(new VarInsnNode(Opcodes.ASTORE, 2));
                    method.instructions.insertBefore(method.instructions.getFirst(), insnList);
                    method.maxStack = Math.max(method.maxStack, 4);
                    modified = true;
                }
            }
            return modified;
        }
        boolean shouldWrapInsn = ((availableGetBytecode && phase == Phase.GetBytecode) || (!availableGetBytecode && phase == Phase.ILaunchPluginServiceBefore));
        boolean shouldModifyReturn = phase == Phase.ILaunchPluginService;
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode methodInsn) {
                    if ((insn.getOpcode() == Opcodes.INVOKEVIRTUAL || insn.getOpcode() == Opcodes.INVOKEINTERFACE) && shouldWrapInsn) {
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
            if (!tickInjected && phase.ordinal() >= 2 && isSameMethod(classNode.name, method, "net/minecraft/server/level/ServerLevel", "m_8793_", "tick", "(Ljava/util/function/BooleanSupplier;)V", false)) {
                InsnList insnList = new InsnList();
                insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
                insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "updateLastTicks", "(Lnet/minecraft/server/level/ServerLevel;)V", false));
                method.instructions.insert(insnList);
                method.maxStack += 1;
                tickInjected = true;
                modified = true;
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
        if (className.equals(superClass) || superClass.equals("java/lang/Object")) return true;
        if (className.equals("java/lang/Object")) return false;
        if (className.startsWith("java/") ||
                className.startsWith("javax/") ||
                className.startsWith("sun/") ||
                className.startsWith("com/sun/") ||
                className.startsWith("jdk/") ||
                className.startsWith("org/lwjgl/") ||
                className.startsWith("org/apache/") ||
                className.startsWith("io/netty/") ||
                className.startsWith("com/google/") ||
                className.startsWith("org/spongepowered/")) {
            return false;
        }
        String currentName = className;
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        while (!currentName.equals("java/lang/Object")) {
            try (InputStream is = classLoader.getResourceAsStream(currentName.concat(".class"))) {
                ClassReader classReader = new ClassReader(Objects.requireNonNull(is));
                currentName = classReader.getSuperName();
                ClassNode classNode = new ClassNode(Opcodes.ASM9);
                classReader.accept(classNode, ClassReader.EXPAND_FRAMES);
                if (classNode.visibleAnnotations != null && classNode.visibleAnnotations.stream().anyMatch(annotationNode -> annotationNode.desc.equals(ONLYIN_DESC) && !((String[]) annotationNode.values.get(annotationNode.values.indexOf("value") + 1))[1].equals(FML_DIST))) {
                    return false;
                }
                if (currentName.equals(superClass)) return true;
                if (isInterface) {
                    for (String interfaceName : classReader.getInterfaces()) {
                        if (isSubclass(interfaceName, superClass, true)) return true;
                    }
                }
            } catch (Throwable e) {
                System.err.println("[DecayTransformer] isSubclass search failed for " + currentName + ": " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    public enum Phase {
        GetBytecode, ILaunchPluginServiceBefore, ILaunchPluginService
    }
}