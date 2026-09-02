package com.maxwell.hyperdamagelib.transformer;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class DecayGenericTransformer implements Opcodes {
    private static final String METHODS = "com/maxwell/hyperdamagelib/transformer/DecayEntityMethods";

    public static boolean transform(ClassNode node) {
        boolean modified = false;
        for (MethodNode mn : node.methods) {
            if ((mn.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0) continue;
            modified |= sanitizeCallerInstructions(mn);
        }
        if (node.name.equals("net/minecraft/world/entity/LivingEntity")) {
            for (MethodNode mn : node.methods) {
                if (isM(mn, "m_6667_", "die", "(Lnet/minecraft/world/damagesource/DamageSource;)V")) {injectGuard(mn, "shouldInterceptDie", "(Lnet/minecraft/world/entity/LivingEntity;)Z", false);modified = true;}
                else if (isM(mn, "m_8119_", "baseTick", "()V")) {injectStatic(mn, "forceStateSync", "(Lnet/minecraft/world/entity/LivingEntity;)V");modified = true;}
                else if (isM(mn, "m_6153_", "tickDeath", "()V")) {injectGuard(mn, "shouldInterceptTickDeath", "(Lnet/minecraft/world/entity/LivingEntity;)Z", false);modified = true;}
                else if (isM(mn, "m_21223_", "getHealth", "()F")) injectReplace(mn, "shouldReplaceHealthMethod", "(Lnet/minecraft/world/entity/Entity;)Z", "replaceGetHealth", "(Lnet/minecraft/world/entity/LivingEntity;)F", FRETURN);
                else if (isM(mn, "m_21224_", "isDeadOrDying", "()Z")) injectReplace(mn, "shouldReplaceHealthMethod", "(Lnet/minecraft/world/entity/Entity;)Z", "replaceIsDeadOrDying", "(Lnet/minecraft/world/entity/Entity;)Z", IRETURN);
                modified |= scanFields(mn);
            }
            modified = true;
        }else if (node.name.equals("net/minecraft/world/entity/Entity")) {
            for (MethodNode mn : node.methods) {
                if (isM(mn, "m_6084_", "isAlive", "()Z")) injectReplace(mn, "shouldReplaceHealthMethod", "(Lnet/minecraft/world/entity/Entity;)Z", "replaceIsAlive", "(Lnet/minecraft/world/entity/Entity;)Z", IRETURN);
                else if (isM(mn, "m_6087_", "isPickable", "()Z")) injectReplace(mn, "shouldReplaceIsPickable", "(Lnet/minecraft/world/entity/Entity;)Z", "replaceIsPickable", "(Lnet/minecraft/world/entity/Entity;)Z", IRETURN);
                else if (isM(mn, "m_6097_", "isAttackable", "()Z")) injectReplace(mn, "shouldReplaceIsAttackable", "(Lnet/minecraft/world/entity/Entity;)Z", "replaceIsAttackable", "(Lnet/minecraft/world/entity/Entity;)Z", IRETURN);
                else if (isM(mn, "m_6094_", "canBeHitByProjectile", "()Z")) injectReplace(mn, "shouldReplaceCanBeHitByProjectile", "(Lnet/minecraft/world/entity/Entity;)Z", "replaceCanBeHitByProjectile", "(Lnet/minecraft/world/entity/Entity;)Z", IRETURN);
                else if (isM(mn, "m_20343_", "setPosRaw", "(DDD)V") || isM(mn, "m_6034_", "setPos", "(DDD)V")) injectGuard(mn, "shouldInterceptSetPos", "(Lnet/minecraft/world/entity/Entity;DDD)Z", true);
                else if (isM(mn, "m_142687_", "setRemoved", "(Lnet/minecraft/world/entity/Entity$RemovalReason;)V")) injectGuard(mn, "shouldInterceptRemoval", "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$RemovalReason;)Z", false);
                else if (isM(mn, "m_6074_", "kill", "()V") || isM(mn, "m_146870_", "discard", "()V")) injectGuard(mn, "shouldInterceptKill", "(Lnet/minecraft/world/entity/Entity;)Z", false);
                else if (isM(mn, "m_20124_", "setPose", "(Lnet/minecraft/world/entity/Pose;)V")) {injectGuard(mn, "shouldInterceptSetPose", "(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Pose;)Z", false);modified = true;}
            }
            modified = true;
        } else if (node.name.equals("net/minecraft/world/level/entity/EntityTickList") || node.name.equals("net/minecraft/world/level/entity/EntityLookup")) {
            boolean isLookup = node.name.endsWith("Lookup");
            for (MethodNode mn : node.methods) {
                if (mn.name.equals("remove")) {
                    injectGuard(mn, isLookup ? "shouldInterceptLookupRemove" : "shouldInterceptTickListRemove", isLookup ? "(Ljava/lang/Object;)Z" : "(Lnet/minecraft/world/entity/Entity;)Z", false);
                    modified = true;
                }
            }
        }
        return modified;
    }

    private static boolean sanitizeCallerInstructions(MethodNode mn) {
        boolean modified = false;
        for (AbstractInsnNode insn : mn.instructions.toArray()) {
            if (!(insn instanceof MethodInsnNode mi)) continue;

            if (mi.getOpcode() == INVOKEVIRTUAL) {
                if (isEntity(mi.owner)) {
                    if (isM(mi, "m_21223_", "getHealth", "()F")) modified |= redir(mi, "getTrueHealth", "(Lnet/minecraft/world/entity/LivingEntity;)F");
                    else if (isM(mi, "m_21224_", "isDeadOrDying", "()Z")) modified |= redir(mi, "isReallyDeadOrDying", "(Lnet/minecraft/world/entity/LivingEntity;)Z");
                    else if (isM(mi, "m_6084_", "isAlive", "()Z")) modified |= redir(mi, "isReallyAlive", "(Lnet/minecraft/world/entity/Entity;)Z");
                    else if (isM(mi, "m_213877_", "isRemoved", "()Z")) modified |= redir(mi, "isReallyRemoved", "(Lnet/minecraft/world/entity/Entity;)Z");
                }

                else if (mi.owner.equals("net/minecraft/network/syncher/SynchedEntityData")) {

                    if (mi.desc.equals("(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)V") && (mi.name.equals("set") || mi.name.equals("m_135381_"))) {
                        mi.setOpcode(INVOKESTATIC);
                        mi.owner = METHODS;
                        mi.name = "interceptDataUpdate";
                        mi.desc = "(Lnet/minecraft/network/syncher/SynchedEntityData;Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;)V";
                        modified = true;
                    }

                    else if (mi.desc.equals("(Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V") && (mi.name.equals("set") || mi.name.startsWith("m_"))) {
                        mi.setOpcode(INVOKESTATIC);
                        mi.owner = METHODS;
                        mi.name = "interceptDataUpdate";
                        mi.desc = "(Lnet/minecraft/network/syncher/SynchedEntityData;Lnet/minecraft/network/syncher/EntityDataAccessor;Ljava/lang/Object;Z)V";
                        modified = true;
                    }
                }
            }

            else if (mi.getOpcode() == INVOKEINTERFACE && !isSys(mi.owner)) {
                String d = mi.desc;
                if (d.startsWith("(FLjava/lang/Object;") && d.endsWith(")F")) {
                    modified |= redirHook(mi, "sanitizeHookHealth", "(Ljava/lang/Object;FLjava/lang/Object;Ljava/lang/Object;)F");
                } else if (d.startsWith("(ZLjava/lang/Object;") && d.endsWith(")Z")) {
                    String n = mi.name.toLowerCase();
                    String target = n.contains("dead") ? "sanitizeHookDeadOrDying" : n.contains("removed") ? "sanitizeHookRemoved" : "sanitizeHookAlive";
                    modified |= redirHook(mi, target, "(Ljava/lang/Object;ZLjava/lang/Object;Ljava/lang/Object;)Z");
                }
            } else if (mi.getOpcode() == INVOKESTATIC && !isSys(mi.owner)) {
                if (mi.desc.equals("(FLnet/minecraft/world/entity/LivingEntity;)F")) modified |= redir(mi, "sanitizeStaticHealth", mi.desc);
                else if (mi.desc.equals("(ZLnet/minecraft/world/entity/LivingEntity;)Z")) modified |= redir(mi, "sanitizeStaticDeadOrDying", mi.desc);
                else if (mi.desc.equals("(ZLnet/minecraft/world/entity/Entity;)Z")) modified |= redir(mi, "sanitizeStaticAlive", mi.desc);
            }
        }
        return modified;
    }
    private static boolean scanFields(MethodNode mn) {
        boolean m = false;
        for (AbstractInsnNode insn : mn.instructions.toArray()) {
            if (insn instanceof FieldInsnNode f && f.getOpcode() == PUTFIELD && f.owner.equals("net/minecraft/world/entity/LivingEntity")) {
                if (f.name.equals("deathTime") || f.name.equals("f_20919_")) {
                    injectFieldHook(mn, f, "sanitizeDeathTimeWrite", "(ILnet/minecraft/world/entity/LivingEntity;)I");
                    m = true;
                }
                else if (f.name.equals("dead") || f.name.equals("f_20890_")) {
                    injectFieldHook(mn, f, "sanitizeDeadFlagWrite", "(ZLnet/minecraft/world/entity/LivingEntity;)Z");
                    m = true;
                }
            }
        }
        return m;
    }

    private static void injectFieldHook(MethodNode mn, FieldInsnNode f, String name, String desc) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(ALOAD, 0)); 
        il.add(new MethodInsnNode(INVOKESTATIC, METHODS, name, desc, false));
        mn.instructions.insertBefore(f, il);
    }
    private static void injectReplace(MethodNode mn, String judge, String judgeDesc, String repl, String replDesc, int ret) {
        LabelNode l = new LabelNode(); InsnList il = new InsnList();
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new MethodInsnNode(INVOKESTATIC, METHODS, judge, judgeDesc, false));
        il.add(new JumpInsnNode(IFEQ, l));
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new MethodInsnNode(INVOKESTATIC, METHODS, repl, replDesc, false));
        il.add(new InsnNode(ret));
        il.add(l); insertHead(mn, il);
    }
    private static void injectStatic(MethodNode mn, String name, String desc) {
        InsnList il = new InsnList();
        il.add(new VarInsnNode(ALOAD, 0));
        il.add(new MethodInsnNode(INVOKESTATIC, METHODS, name, desc, false));
        insertHead(mn, il);
    }
    private static void injectGuard(MethodNode mn, String judge, String judgeDesc, boolean isPos) {
        LabelNode l = new LabelNode();
        InsnList il = new InsnList();

        if (isPos) {
            il.add(new VarInsnNode(ALOAD, 0));
            il.add(new VarInsnNode(DLOAD, 1));
            il.add(new VarInsnNode(DLOAD, 3));
            il.add(new VarInsnNode(DLOAD, 5));
        } else {
            il.add(new VarInsnNode(ALOAD, mn.name.equals("remove") ? 1 : 0));
            if (judgeDesc.contains("RemovalReason") || judgeDesc.contains("Pose")) {
                il.add(new VarInsnNode(ALOAD, 1));
            }
        }

        il.add(new MethodInsnNode(INVOKESTATIC, METHODS, judge, judgeDesc, false));
        il.add(new JumpInsnNode(IFEQ, l));
        il.add(new InsnNode(RETURN));
        il.add(l);
        insertHead(mn, il);
    }

    private static void insertHead(MethodNode mn, InsnList il) {
        AbstractInsnNode f = mn.instructions.getFirst();
        while (f != null && (f instanceof LabelNode || f instanceof LineNumberNode || f instanceof FrameNode)) f = f.getNext();
        if (f != null) mn.instructions.insertBefore(f, il); else mn.instructions.add(il);
    }

    private static boolean redir(MethodInsnNode mi, String n, String d) { mi.setOpcode(INVOKESTATIC); mi.owner = METHODS; mi.name = n; mi.desc = d; return true; }
    private static boolean redirHook(MethodInsnNode mi, String n, String d) { mi.setOpcode(INVOKESTATIC); mi.owner = METHODS; mi.name = n; mi.desc = d; mi.itf = false; return true; }
    private static boolean isM(MethodNode mn, String s, String n, String d) { return (mn.name.equals(s) || mn.name.equals(n)) && mn.desc.equals(d); }
    private static boolean isM(MethodInsnNode mi, String s, String n, String d) { return (mi.name.equals(s) || mi.name.equals(n)) && mi.desc.equals(d); }
    private static boolean isEntity(String o) {

        return o.equals("net/minecraft/world/entity/Entity") ||
                o.equals("net/minecraft/world/entity/LivingEntity") ||
                o.equals("net/minecraft/world/entity/Mob") ||
                o.equals("net/minecraft/world/entity/player/Player") ||
                o.equals("net/minecraft/server/level/ServerPlayer") ||
                o.equals("net/minecraft/client/player/LocalPlayer");
    }
    private static boolean isSys(String o) { return o.startsWith("java/") || o.startsWith("net/minecraft/") || o.startsWith("net/minecraftforge/") || o.startsWith("com/maxwell/hyperdamagelib/"); }
}