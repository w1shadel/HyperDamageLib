package com.maxwell.hyperdamagelib.transformer;

import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

public class DecayGenericTransformer {
    static final String ENTITY_METHODS = "com/maxwell/hyperdamagelib/transformer/DecayEntityMethods";
    public static List<String> exclusivePackages = new ArrayList<>();
    static boolean initialized = false;

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
        if (exclusivePackages.stream().anyMatch(packageName -> classNode.name.startsWith(packageName))) {
            return false;
        }
        boolean modified = false;
        if (classNode.name.equals("net/minecraft/world/entity/LivingEntity")) {
            for (MethodNode method : classNode.methods) {
                if (isMethodNamed(method, "m_21223_", "getHealth", "()F")) {
                    injectHeadClean(method, "shouldReplaceHealthMethod", "(Lnet/minecraft/world/entity/Entity;)Z", "replaceGetHealth", "(Lnet/minecraft/world/entity/LivingEntity;)F", Opcodes.FRETURN);
                    modified = true;
                } else if (isMethodNamed(method, "m_21224_", "isDeadOrDying", "()Z")) {
                    injectHeadClean(method, "shouldReplaceHealthMethod", "(Lnet/minecraft/world/entity/Entity;)Z", "replaceIsDeadOrDying", "(Lnet/minecraft/world/entity/Entity;)Z", Opcodes.IRETURN);
                    modified = true;
                } else if (isMethodNamed(method, "m_7301_", "canBeAffected", "(Lnet/minecraft/world/effect/MobEffectInstance;)Z")) {
                    injectHeadClean(method, "shouldInterceptCanBeAffected", "(Lnet/minecraft/world/entity/Entity;)Z", "replaceCanBeAffected", "(Lnet/minecraft/world/entity/LivingEntity;)Z", Opcodes.IRETURN);
                    modified = true;
                } else if (isMethodNamed(method, "m_7822_", "handleEntityEvent", "(B)V")) {
                    injectHandleEntityEventProtection(method);
                    modified = true;
                }
            }
        } else if (classNode.name.equals("net/minecraft/world/entity/Entity")) {
            for (MethodNode method : classNode.methods) {
                if (isMethodNamed(method, "m_6084_", "isAlive", "()Z")) {
                    injectHeadClean(method, "shouldReplaceHealthMethod", "(Lnet/minecraft/world/entity/Entity;)Z", "replaceIsAlive", "(Lnet/minecraft/world/entity/Entity;)Z", Opcodes.IRETURN);
                    modified = true;
                } else if (isMethodNamed(method, "m_6087_", "isPickable", "()Z")) {
                    injectHeadClean(method, "shouldReplaceIsPickable", "(Lnet/minecraft/world/entity/Entity;)Z", "replaceIsPickable", "(Lnet/minecraft/world/entity/Entity;)Z", Opcodes.IRETURN);
                    modified = true;
                } else if (isMethodNamed(method, "m_6097_", "isAttackable", "()Z")) {
                    injectHeadClean(method, "shouldReplaceIsAttackable", "(Lnet/minecraft/world/entity/Entity;)Z", "replaceIsAttackable", "(Lnet/minecraft/world/entity/Entity;)Z", Opcodes.IRETURN);
                    modified = true;
                } else if (isMethodNamed(method, "m_6094_", "canBeHitByProjectile", "()Z")) {
                    injectHeadClean(method, "shouldReplaceCanBeHitByProjectile", "(Lnet/minecraft/world/entity/Entity;)Z", "replaceCanBeHitByProjectile", "(Lnet/minecraft/world/entity/Entity;)Z", Opcodes.IRETURN);
                    modified = true;
                } else if (isMethodNamed(method, "m_6096_", "isPushable", "()Z")) {
                    injectHeadClean(method, "shouldReplaceIsPushable", "(Lnet/minecraft/world/entity/Entity;)Z", "replaceIsPushable", "(Lnet/minecraft/world/entity/Entity;)Z", Opcodes.IRETURN);
                    modified = true;
                } else if (isMethodNamed(method, "m_20343_", "setPosRaw", "(DDD)V") || isMethodNamed(method, "m_6034_", "setPos", "(DDD)V")) {
                    injectPosProtection(method);
                    modified = true;
                } else if (isMethodNamed(method, "m_142687_", "setRemoved", "(Lnet/minecraft/world/entity/Entity$RemovalReason;)V")) {
                    injectRemovalProtection(method);
                    modified = true;
                } else if (isMethodNamed(method, "m_6074_", "kill", "()V") || isMethodNamed(method, "m_146870_", "discard", "()V")) {
                    injectVoidProtection(method, "shouldInterceptKill", "(Lnet/minecraft/world/entity/Entity;)Z");
                    modified = true;
                }
            }
        } else if (classNode.name.equals("net/minecraft/world/level/entity/EntityTickList")) {
            for (MethodNode method : classNode.methods) {
                if (isMethodNamed(method, "m_156914_", "remove", "(Lnet/minecraft/world/entity/Entity;)V")) {
                    injectTickListProtection(method);
                    modified = true;
                }
            }
        } else if (classNode.name.equals("net/minecraft/world/level/entity/EntityLookup")) {
            for (MethodNode method : classNode.methods) {
                if (isMethodNamed(method, "m_156820_", "remove", "(Lnet/minecraft/world/level/entity/EntityAccess;)V")) {
                    injectLookupProtection(method);
                    modified = true;
                }
            }
        }
        return modified;
    }

    private static void injectPosProtection(MethodNode method) {
        LabelNode skipLabel = new LabelNode(new Label());
        InsnList list = new InsnList();
        list.add(new VarInsnNode(Opcodes.ALOAD, 0));
        list.add(new VarInsnNode(Opcodes.DLOAD, 1));
        list.add(new VarInsnNode(Opcodes.DLOAD, 3));
        list.add(new VarInsnNode(Opcodes.DLOAD, 5));
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "shouldInterceptSetPos", "(Lnet/minecraft/world/entity/Entity;DDD)Z", false));
        list.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel));
        list.add(new InsnNode(Opcodes.RETURN));
        list.add(skipLabel);
        list.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        insertAtRealHead(method, list);
        method.maxStack += 4;
    }

    private static void injectHandleEntityEventProtection(MethodNode method) {
        LabelNode skipLabel = new LabelNode(new Label());
        InsnList list = new InsnList();
        list.add(new VarInsnNode(Opcodes.ALOAD, 0));
        list.add(new VarInsnNode(Opcodes.ILOAD, 1));
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "shouldInterceptEntityEvent", "(Lnet/minecraft/world/entity/Entity;B)Z", false));
        list.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel));
        list.add(new InsnNode(Opcodes.RETURN));
        list.add(skipLabel);
        list.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        insertAtRealHead(method, list);
        method.maxStack += 2;
    }

    private static void injectRemovalProtection(MethodNode method) {
        LabelNode skipLabel = new LabelNode(new Label());
        InsnList list = new InsnList();
        list.add(new VarInsnNode(Opcodes.ALOAD, 0));
        list.add(new VarInsnNode(Opcodes.ALOAD, 1));
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "shouldInterceptRemoval", "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$RemovalReason;)Z", false));
        list.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel));
        list.add(new InsnNode(Opcodes.RETURN));
        list.add(skipLabel);
        list.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        insertAtRealHead(method, list);
        method.maxStack += 2;
    }

    private static void injectVoidProtection(MethodNode method, String judgeName, String judgeDesc) {
        LabelNode skipLabel = new LabelNode(new Label());
        InsnList list = new InsnList();
        list.add(new VarInsnNode(Opcodes.ALOAD, 0));
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, judgeName, judgeDesc, false));
        list.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel));
        list.add(new InsnNode(Opcodes.RETURN));
        list.add(skipLabel);
        list.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        insertAtRealHead(method, list);
        method.maxStack += 2;
    }

    private static void injectTickListProtection(MethodNode method) {
        LabelNode skipLabel = new LabelNode(new Label());
        InsnList list = new InsnList();
        list.add(new VarInsnNode(Opcodes.ALOAD, 1));
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "shouldInterceptTickListRemove", "(Lnet/minecraft/world/entity/Entity;)Z", false));
        list.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel));
        list.add(new InsnNode(Opcodes.RETURN));
        list.add(skipLabel);
        list.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        insertAtRealHead(method, list);
        method.maxStack += 2;
    }

    private static void injectLookupProtection(MethodNode method) {
        LabelNode skipLabel = new LabelNode(new Label());
        InsnList list = new InsnList();
        list.add(new VarInsnNode(Opcodes.ALOAD, 1));
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "shouldInterceptLookupRemove", "(Ljava/lang/Object;)Z", false));
        list.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel));
        list.add(new InsnNode(Opcodes.RETURN));
        list.add(skipLabel);
        list.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        insertAtRealHead(method, list);
        method.maxStack += 2;
    }

    private static void injectHeadClean(MethodNode method, String judgeName, String judgeDesc, String replaceName, String replaceDesc, int returnOpcode) {
        LabelNode skipLabel = new LabelNode(new Label());
        InsnList insnList = new InsnList();
        insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, judgeName, judgeDesc, false));
        insnList.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel));
        insnList.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insnList.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, replaceName, replaceDesc, false));
        insnList.add(new InsnNode(returnOpcode));
        insnList.add(skipLabel);
        insnList.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        insertAtRealHead(method, insnList);
        method.maxStack += 2;
    }

    private static void insertAtRealHead(MethodNode method, InsnList insns) {
        AbstractInsnNode firstRealInsn = method.instructions.getFirst();
        while (firstRealInsn != null && (firstRealInsn instanceof LabelNode || firstRealInsn instanceof LineNumberNode || firstRealInsn instanceof FrameNode)) {
            if (firstRealInsn instanceof FrameNode) {
                AbstractInsnNode toRemove = firstRealInsn;
                firstRealInsn = firstRealInsn.getNext();
                method.instructions.remove(toRemove);
                continue;
            }
            firstRealInsn = firstRealInsn.getNext();
        }
        if (firstRealInsn != null) {
            method.instructions.insertBefore(firstRealInsn, insns);
        } else {
            method.instructions.add(insns);
        }
    }

    private static boolean isMethodNamed(MethodNode method, String obfName, String name, String desc) {
        return (obfName.equals(method.name) || name.equals(method.name)) && desc.equals(method.desc);
    }

    public enum Phase {
        ILaunchPluginService
    }
}