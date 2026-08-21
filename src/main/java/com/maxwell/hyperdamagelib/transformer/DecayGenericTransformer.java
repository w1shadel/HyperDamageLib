package com.maxwell.hyperdamagelib.transformer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class DecayGenericTransformer {
    private static final String ENTITY_METHODS = "com/maxwell/hyperdamagelib/transformer/DecayEntityMethods";

    private DecayGenericTransformer() {
    }

    public static boolean transform(ClassNode classNode) {
        boolean modified = false;
        for (MethodNode method : classNode.methods) {
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
            modified |= sanitizeCallerInstructions(method);
        }
        if (classNode.name.equals("net/minecraft/world/entity/LivingEntity")) {
            for (MethodNode method : classNode.methods) {
                if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
                if (isMethod(method, "m_21223_", "getHealth", "()F")) {
                    injectHeadReturn(method, "shouldReplaceHealthMethod", "(Lnet/minecraft/world/entity/Entity;)Z", "replaceGetHealth", "(Lnet/minecraft/world/entity/LivingEntity;)F", Opcodes.FRETURN);
                    modified = true;
                } else if (isMethod(method, "m_21224_", "isDeadOrDying", "()Z")) {
                    injectHeadReturn(method, "shouldReplaceHealthMethod", "(Lnet/minecraft/world/entity/Entity;)Z", "replaceIsDeadOrDying", "(Lnet/minecraft/world/entity/Entity;)Z", Opcodes.IRETURN);
                    modified = true;
                }
            }
        } else if (classNode.name.equals("net/minecraft/world/entity/Entity")) {
            for (MethodNode method : classNode.methods) {
                if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
                if (isMethod(method, "m_6084_", "isAlive", "()Z")) {
                    injectHeadReturn(method, "shouldReplaceHealthMethod", "(Lnet/minecraft/world/entity/Entity;)Z", "replaceIsAlive", "(Lnet/minecraft/world/entity/Entity;)Z", Opcodes.IRETURN);
                    modified = true;
                } else if (isMethod(method, "m_6087_", "isPickable", "()Z")) {
                    injectHeadReturn(method, "shouldReplaceIsPickable", "(Lnet/minecraft/world/entity/Entity;)Z", "replaceIsPickable", "(Lnet/minecraft/world/entity/Entity;)Z", Opcodes.IRETURN);
                    modified = true;
                } else if (isMethod(method, "m_6097_", "isAttackable", "()Z")) {
                    injectHeadReturn(method, "shouldReplaceIsAttackable", "(Lnet/minecraft/world/entity/Entity;)Z", "replaceIsAttackable", "(Lnet/minecraft/world/entity/Entity;)Z", Opcodes.IRETURN);
                    modified = true;
                } else if (isMethod(method, "m_6094_", "canBeHitByProjectile", "()Z")) {
                    injectHeadReturn(method, "shouldReplaceCanBeHitByProjectile", "(Lnet/minecraft/world/entity/Entity;)Z", "replaceCanBeHitByProjectile", "(Lnet/minecraft/world/entity/Entity;)Z", Opcodes.IRETURN);
                    modified = true;
                } else if (isMethod(method, "m_20343_", "setPosRaw", "(DDD)V") || isMethod(method, "m_6034_", "setPos", "(DDD)V")) {
                    injectPosGuard(method);
                    modified = true;
                } else if (isMethod(method, "m_142687_", "setRemoved", "(Lnet/minecraft/world/entity/Entity$RemovalReason;)V")) {
                    injectRemovalGuard(method);
                    modified = true;
                } else if (isMethod(method, "m_6074_", "kill", "()V") || isMethod(method, "m_146870_", "discard", "()V")) {
                    injectVoidGuard(method);
                    modified = true;
                }
            }
        } else if (classNode.name.equals("net/minecraft/world/level/entity/EntityTickList")) {
            for (MethodNode method : classNode.methods) {
                if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
                if (isMethod(method, "m_156914_", "remove", "(Lnet/minecraft/world/entity/Entity;)V")) {
                    injectTickListGuard(method);
                    modified = true;
                }
            }
        } else if (classNode.name.equals("net/minecraft/world/level/entity/EntityLookup")) {
            for (MethodNode method : classNode.methods) {
                if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) continue;
                if (isMethod(method, "m_156820_", "remove", "(Lnet/minecraft/world/level/entity/EntityAccess;)V")) {
                    injectLookupGuard(method);
                    modified = true;
                }
            }
        }
        return modified;
    }

    private static boolean sanitizeCallerInstructions(MethodNode method) {
        boolean modified = false;
        for (AbstractInsnNode insn : method.instructions.toArray()) {
            if (insn instanceof MethodInsnNode minsn) {
                if (minsn.getOpcode() == Opcodes.INVOKEVIRTUAL) {
                    if (isLivingEntityClass(minsn.owner)) {
                        if ((minsn.name.equals("getHealth") || minsn.name.equals("m_21223_")) && minsn.desc.equals("()F")) {
                            minsn.setOpcode(Opcodes.INVOKESTATIC);
                            minsn.owner = ENTITY_METHODS;
                            minsn.name = "getTrueHealth";
                            minsn.desc = "(Lnet/minecraft/world/entity/LivingEntity;)F";
                            modified = true;
                        } else if ((minsn.name.equals("isDeadOrDying") || minsn.name.equals("m_21224_")) && minsn.desc.equals("()Z")) {
                            minsn.setOpcode(Opcodes.INVOKESTATIC);
                            minsn.owner = ENTITY_METHODS;
                            minsn.name = "isReallyDeadOrDying";
                            minsn.desc = "(Lnet/minecraft/world/entity/LivingEntity;)Z";
                            modified = true;
                        }
                    }
                    if (isAnyEntityClass(minsn.owner)) {
                        if ((minsn.name.equals("isAlive") || minsn.name.equals("m_6084_")) && minsn.desc.equals("()Z")) {
                            minsn.setOpcode(Opcodes.INVOKESTATIC);
                            minsn.owner = ENTITY_METHODS;
                            minsn.name = "isReallyAlive";
                            minsn.desc = "(Lnet/minecraft/world/entity/Entity;)Z";
                            modified = true;
                        } else if ((minsn.name.equals("isRemoved") || minsn.name.equals("m_213877_")) && minsn.desc.equals("()Z")) {
                            minsn.setOpcode(Opcodes.INVOKESTATIC);
                            minsn.owner = ENTITY_METHODS;
                            minsn.name = "isReallyRemoved";
                            minsn.desc = "(Lnet/minecraft/world/entity/Entity;)Z";
                            modified = true;
                        }
                    }
                } else if (minsn.getOpcode() == Opcodes.INVOKEINTERFACE && !isSystemPackage(minsn.owner)) {
                    String desc = minsn.desc;
                    String name = minsn.name;
                    if (desc.startsWith("(FLjava/lang/Object;") && desc.endsWith(")F")) {
                        minsn.setOpcode(Opcodes.INVOKESTATIC);
                        minsn.owner = ENTITY_METHODS;
                        minsn.name = "sanitizeHookHealth";
                        minsn.desc = "(Ljava/lang/Object;FLjava/lang/Object;Ljava/lang/Object;)F";
                        minsn.itf = false;
                        modified = true;
                    } else if (desc.startsWith("(ZLjava/lang/Object;") && desc.endsWith(")Z")) {
                        minsn.setOpcode(Opcodes.INVOKESTATIC);
                        minsn.owner = ENTITY_METHODS;
                        minsn.itf = false;
                        minsn.desc = "(Ljava/lang/Object;ZLjava/lang/Object;Ljava/lang/Object;)Z";
                        if (name.contains("Dead") || name.contains("dead")) {
                            minsn.name = "sanitizeHookDeadOrDying";
                        } else if (name.contains("Removed") || name.contains("removed")) {
                            minsn.name = "sanitizeHookRemoved";
                        } else {
                            minsn.name = "sanitizeHookAlive";
                        }
                        modified = true;
                    }
                } else if (minsn.getOpcode() == Opcodes.INVOKESTATIC && !isSystemPackage(minsn.owner)) {
                    if (minsn.desc.equals("(FLnet/minecraft/world/entity/LivingEntity;)F")) {
                        minsn.owner = ENTITY_METHODS;
                        minsn.name = "sanitizeStaticHealth";
                        modified = true;
                    } else if (minsn.desc.equals("(ZLnet/minecraft/world/entity/LivingEntity;)Z")) {
                        minsn.owner = ENTITY_METHODS;
                        minsn.name = "sanitizeStaticDeadOrDying";
                        modified = true;
                    } else if (minsn.desc.equals("(ZLnet/minecraft/world/entity/Entity;)Z")) {
                        minsn.owner = ENTITY_METHODS;
                        minsn.name = "sanitizeStaticAlive";
                        modified = true;
                    }
                }
            }
        }
        return modified;
    }

    private static boolean isLivingEntityClass(String owner) {
        return owner.equals("net/minecraft/world/entity/LivingEntity") ||
                owner.equals("net/minecraft/world/entity/player/Player") ||
                owner.equals("net/minecraft/server/level/ServerPlayer") ||
                owner.equals("net/minecraft/client/player/LocalPlayer") ||
                owner.equals("net/minecraft/world/entity/Mob");
    }

    private static boolean isAnyEntityClass(String owner) {
        return isLivingEntityClass(owner) || owner.equals("net/minecraft/world/entity/Entity");
    }

    private static boolean isSystemPackage(String owner) {
        return owner.startsWith("net/minecraft/") ||
                owner.startsWith("com/mojang/") ||
                owner.startsWith("java/") ||
                owner.startsWith("jdk/") ||
                owner.startsWith("net/minecraftforge/") ||
                owner.startsWith("com/maxwell/hyperdamagelib/");
    }

    private static void injectHeadReturn(MethodNode method, String judgeName, String judgeDesc, String replaceName, String replaceDesc, int returnOpcode) {
        LabelNode skipLabel = new LabelNode();
        InsnList insns = new InsnList();
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, judgeName, judgeDesc, false));
        insns.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, replaceName, replaceDesc, false));
        insns.add(new InsnNode(returnOpcode));
        insns.add(skipLabel);
        insertAtRealHead(method, insns);
        method.maxStack = Math.max(method.maxStack, 4);
    }

    private static void injectPosGuard(MethodNode method) {
        LabelNode skipLabel = new LabelNode();
        InsnList insns = new InsnList();
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(new VarInsnNode(Opcodes.DLOAD, 1));
        insns.add(new VarInsnNode(Opcodes.DLOAD, 3));
        insns.add(new VarInsnNode(Opcodes.DLOAD, 5));
        insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "shouldInterceptSetPos", "(Lnet/minecraft/world/entity/Entity;DDD)Z", false));
        insns.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel));
        insns.add(new InsnNode(Opcodes.RETURN));
        insns.add(skipLabel);
        insertAtRealHead(method, insns);
        method.maxStack = Math.max(method.maxStack, 8);
    }

    private static void injectRemovalGuard(MethodNode method) {
        LabelNode skipLabel = new LabelNode();
        InsnList insns = new InsnList();
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
        insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "shouldInterceptRemoval", "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$RemovalReason;)Z", false));
        insns.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel));
        insns.add(new InsnNode(Opcodes.RETURN));
        insns.add(skipLabel);
        insertAtRealHead(method, insns);
        method.maxStack = Math.max(method.maxStack, 4);
    }

    private static void injectVoidGuard(MethodNode method) {
        LabelNode skipLabel = new LabelNode();
        InsnList insns = new InsnList();
        insns.add(new VarInsnNode(Opcodes.ALOAD, 0));
        insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "shouldInterceptKill", "(Lnet/minecraft/world/entity/Entity;)Z", false));
        insns.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel));
        insns.add(new InsnNode(Opcodes.RETURN));
        insns.add(skipLabel);
        insertAtRealHead(method, insns);
        method.maxStack = Math.max(method.maxStack, 4);
    }

    private static void injectTickListGuard(MethodNode method) {
        LabelNode skipLabel = new LabelNode();
        InsnList insns = new InsnList();
        insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
        insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "shouldInterceptTickListRemove", "(Lnet/minecraft/world/entity/Entity;)Z", false));
        insns.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel));
        insns.add(new InsnNode(Opcodes.RETURN));
        insns.add(skipLabel);
        insertAtRealHead(method, insns);
        method.maxStack = Math.max(method.maxStack, 4);
    }

    private static void injectLookupGuard(MethodNode method) {
        LabelNode skipLabel = new LabelNode();
        InsnList insns = new InsnList();
        insns.add(new VarInsnNode(Opcodes.ALOAD, 1));
        insns.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "shouldInterceptLookupRemove", "(Ljava/lang/Object;)Z", false));
        insns.add(new JumpInsnNode(Opcodes.IFEQ, skipLabel));
        insns.add(new InsnNode(Opcodes.RETURN));
        insns.add(skipLabel);
        insertAtRealHead(method, insns);
        method.maxStack += 4;
    }

    private static void insertAtRealHead(MethodNode method, InsnList insns) {
        AbstractInsnNode first = method.instructions.getFirst();
        while (first != null && (first instanceof LabelNode || first instanceof LineNumberNode || first instanceof FrameNode)) {
            first = first.getNext();
        }
        if (first != null) {
            method.instructions.insertBefore(first, insns);
        } else {
            method.instructions.add(insns);
        }
    }

    private static boolean isMethod(MethodNode method, String obfName, String name, String desc) {
        return (obfName.equals(method.name) || name.equals(method.name)) && desc.equals(method.desc);
    }
}