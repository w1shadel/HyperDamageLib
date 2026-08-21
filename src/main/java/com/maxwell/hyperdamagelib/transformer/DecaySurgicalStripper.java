package com.maxwell.hyperdamagelib.transformer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;

public class DecaySurgicalStripper {
    private static final String ENTITY_METHODS = "com/maxwell/hyperdamagelib/transformer/DecayEntityMethods";

    public static boolean stripForeignHooks(String className, MethodNode method) {
        boolean modified = false;
        AbstractInsnNode[] insns = method.instructions.toArray();
        for (int i = 0; i < insns.length; i++) {
            AbstractInsnNode insn = insns[i];
            if (insn instanceof MethodInsnNode minsn && (insn.getOpcode() == Opcodes.INVOKEVIRTUAL || insn.getOpcode() == Opcodes.INVOKEINTERFACE)) {
                if (isVitalMethod(minsn)) {
                    if (stripHookAtCallSite(className, method, minsn, i, insns)) {
                        modified = true;
                    }
                }
            }
        }
        return modified;
    }

    private static boolean stripHookAtCallSite(String className, MethodNode method, MethodInsnNode callInsn, int callIndex, AbstractInsnNode[] insns) {
        boolean cleaned = false;
        AbstractInsnNode prevNode = getPreviousRealInsn(callInsn);
        boolean hasPreDup = (prevNode != null && prevNode.getOpcode() == Opcodes.DUP);
        List<AbstractInsnNode> postNodes = getNextRealInsns(callInsn, 8);
        if (postNodes.isEmpty()) return false;
        if (postNodes.size() >= 6 &&
                postNodes.get(0).getOpcode() == Opcodes.GETSTATIC &&
                postNodes.get(1).getOpcode() == Opcodes.DUP_X2 &&
                postNodes.get(2).getOpcode() == Opcodes.POP &&
                postNodes.get(3).getOpcode() == Opcodes.SWAP &&
                postNodes.get(4).getOpcode() == Opcodes.GETSTATIC &&
                postNodes.get(5) instanceof MethodInsnNode hookMinsn && isForeignHook(hookMinsn)) {
            if (hasPreDup) {
                method.instructions.remove(prevNode);
            }
            for (int k = 0; k < 6; k++) {
                method.instructions.remove(postNodes.get(k));
            }
            cleaned = true;
        } else if (postNodes.size() >= 2 &&
                postNodes.get(0).getOpcode() == Opcodes.SWAP &&
                postNodes.get(1) instanceof MethodInsnNode hookMinsn && isForeignHook(hookMinsn)) {
            if (hasPreDup) {
                method.instructions.remove(prevNode);
            }
            method.instructions.remove(postNodes.get(0));
            method.instructions.remove(postNodes.get(1));
            cleaned = true;
        }
        if (cleaned) {
            insertCleanHDLWrapper(method, callInsn);
        }
        return cleaned;
    }

    private static void insertCleanHDLWrapper(MethodNode method, MethodInsnNode callInsn) {
        String methodName = callInsn.name;
        if (methodName.equals("getHealth") || methodName.equals("m_21223_")) {
            method.instructions.insertBefore(callInsn, new InsnNode(Opcodes.DUP));
            InsnList list = new InsnList();
            list.add(new InsnNode(Opcodes.SWAP));
            list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "getHealth", "(FLnet/minecraft/world/entity/LivingEntity;)F", false));
            method.instructions.insert(callInsn, list);
            method.maxStack += 2;
        } else if (methodName.equals("isAlive") || methodName.equals("m_6084_")) {
            method.instructions.insertBefore(callInsn, new InsnNode(Opcodes.DUP));
            InsnList list = new InsnList();
            list.add(new InsnNode(Opcodes.SWAP));
            list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "isAlive", "(ZLnet/minecraft/world/entity/Entity;)Z", false));
            method.instructions.insert(callInsn, list);
            method.maxStack += 2;
        } else if (methodName.equals("isDeadOrDying") || methodName.equals("m_21224_")) {
            method.instructions.insertBefore(callInsn, new InsnNode(Opcodes.DUP));
            InsnList list = new InsnList();
            list.add(new InsnNode(Opcodes.SWAP));
            list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, ENTITY_METHODS, "isDeadOrDying", "(ZLnet/minecraft/world/entity/LivingEntity;)Z", false));
            method.instructions.insert(callInsn, list);
            method.maxStack += 2;
        }
    }

    private static AbstractInsnNode getPreviousRealInsn(AbstractInsnNode node) {
        AbstractInsnNode prev = node.getPrevious();
        while (prev != null && (prev instanceof LabelNode || prev instanceof LineNumberNode || prev instanceof FrameNode)) {
            prev = prev.getPrevious();
        }
        return prev;
    }

    private static List<AbstractInsnNode> getNextRealInsns(AbstractInsnNode node, int count) {
        List<AbstractInsnNode> list = new ArrayList<>();
        AbstractInsnNode next = node.getNext();
        while (next != null && list.size() < count) {
            if (!(next instanceof LabelNode) && !(next instanceof LineNumberNode) && !(next instanceof FrameNode)) {
                list.add(next);
            }
            next = next.getNext();
        }
        return list;
    }

    private static boolean isVitalMethod(MethodInsnNode minsn) {
        String name = minsn.name;
        String desc = minsn.desc;
        return isEntityOrLiving(minsn.owner) && (
                ((name.equals("getHealth") || name.equals("m_21223_")) && desc.equals("()F")) ||
                        ((name.equals("isAlive") || name.equals("m_6084_")) && desc.equals("()Z")) ||
                        ((name.equals("isDeadOrDying") || name.equals("m_21224_")) && desc.equals("()Z"))
        );
    }

    private static boolean isEntityOrLiving(String owner) {
        return owner.equals("net/minecraft/world/entity/LivingEntity") ||
                owner.equals("net/minecraft/world/entity/Entity") ||
                owner.equals("net/minecraft/world/entity/player/Player") ||
                owner.equals("net/minecraft/server/level/ServerPlayer") ||
                owner.equals("net/minecraft/client/player/LocalPlayer");
    }

    private static boolean isForeignHook(MethodInsnNode minsn) {
        String owner = minsn.owner;
        return !owner.startsWith("net/minecraft/") &&
                !owner.startsWith("java/") &&
                !owner.startsWith("com/mojang/") &&
                !owner.startsWith("com/maxwell/hyperdamagelib/");
    }
}