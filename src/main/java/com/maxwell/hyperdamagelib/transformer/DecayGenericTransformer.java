package com.maxwell.hyperdamagelib.transformer;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

public final class DecayGenericTransformer implements Opcodes {
    private static final String METHODS = "com/maxwell/hyperdamagelib/transformer/DecayEntityMethods";

    public static int transform(ClassNode classNode) {
        if (classNode.name.startsWith("com/maxwell/hyperdamagelib/transformer/") ||
                classNode.name.startsWith("net/minecraft/world/level/chunk/storage/") ||
                classNode.name.startsWith("net/minecraft/client/gui/")) {
            return 0;
        }

        boolean modified = false;

        if (classNode.name.equals("net/minecraft/world/level/entity/EntityLookup")) {
            for (MethodNode method : classNode.methods) {
                if (method.name.equals("<init>")) {
                    for (AbstractInsnNode insn : method.instructions.toArray()) {
                        if (insn instanceof FieldInsnNode fn && fn.getOpcode() == PUTFIELD) {
                            if (fn.name.equals("byId") || fn.name.equals("f_156807_")) {
                                InsnList list = new InsnList();
                                list.add(new MethodInsnNode(INVOKESTATIC, METHODS, "hdl$wrapEntityMap", "(Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;)Lit/unimi/dsi/fastutil/ints/Int2ObjectMap;", false));
                                method.instructions.insertBefore(fn, list);
                                modified = true;
                            }
                        }
                    }
                }
            }
        }

        if (classNode.name.equals("net/minecraft/world/entity/Entity")) {
            for (MethodNode method : classNode.methods) {
                if ((method.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0) continue;
                if ((method.name.equals("isPickable") || method.name.equals("m_6087_")) && method.desc.equals("()Z")) {
                    injectForceTrue(method);
                    modified = true;
                } else if ((method.name.equals("isAttackable") || method.name.equals("m_6097_")) && method.desc.equals("()Z")) {
                    injectForceTrue(method);
                    modified = true;
                }
            }
        }

        if (classNode.name.equals("net/minecraft/world/entity/LivingEntity")) {
            for (MethodNode method : classNode.methods) {
                if ((method.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0) continue;
                if (method.instructions == null || method.instructions.getFirst() == null) continue;

                if ((method.name.equals("setHealth") || method.name.equals("m_21153_")) && method.desc.equals("(F)V")) {
                    InsnList list = new InsnList();
                    list.add(new VarInsnNode(ALOAD, 0));
                    list.add(new VarInsnNode(FLOAD, 1));
                    list.add(new MethodInsnNode(INVOKESTATIC, METHODS, "hdl$hookSetHealth", "(Ljava/lang/Object;F)F", false));
                    list.add(new VarInsnNode(FSTORE, 1));

                    method.instructions.insertBefore(method.instructions.getFirst(), list);
                    method.maxStack = Math.max(method.maxStack, 2);
                    modified = true;
                }
            }
        }

        for (MethodNode method : classNode.methods) {
            if ((method.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0) continue;

            if (method.name.equals("addAdditionalSaveData") || method.name.equals("m_7380_") ||
                    method.name.equals("saveWithoutId") || method.name.equals("m_20223_")) {
                continue;
            }

            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode mi)) continue;

                if ((mi.getOpcode() == INVOKEVIRTUAL || mi.getOpcode() == INVOKEINTERFACE) && isTargetEntity(mi.owner)) {
                    if ((mi.name.equals("getHealth") || mi.name.equals("m_21223_")) && mi.desc.equals("()F")) {
                        mi.setOpcode(INVOKESTATIC);
                        mi.owner = METHODS;
                        mi.name = "hdl$getHealth";
                        mi.desc = "(Ljava/lang/Object;)F";
                        mi.itf = false;
                        modified = true;
                    } else if ((mi.name.equals("isDeadOrDying") || mi.name.equals("m_21224_")) && mi.desc.equals("()Z")) {
                        mi.setOpcode(INVOKESTATIC);
                        mi.owner = METHODS;
                        mi.name = "hdl$isDeadOrDying";
                        mi.desc = "(Ljava/lang/Object;)Z";
                        mi.itf = false;
                        modified = true;
                    } else if ((mi.name.equals("isAlive") || mi.name.equals("m_6084_")) && mi.desc.equals("()Z")) {
                        mi.setOpcode(INVOKESTATIC);
                        mi.owner = METHODS;
                        mi.name = "hdl$isAlive";
                        mi.desc = "(Ljava/lang/Object;)Z";
                        mi.itf = false;
                        modified = true;
                    } else if ((mi.name.equals("isRemoved") || mi.name.equals("m_213877_") || mi.name.equals("m_240725_")) && mi.desc.equals("()Z")) {
                        mi.setOpcode(INVOKESTATIC);
                        mi.owner = METHODS;
                        mi.name = "hdl$isRemoved";
                        mi.desc = "(Ljava/lang/Object;)Z";
                        mi.itf = false;
                        modified = true;
                    }
                }

                else if (mi.getOpcode() == INVOKEINTERFACE && !isSys(mi.owner)) {
                    String d = mi.desc;
                    if (d.startsWith("(FLjava/lang/Object;") && d.endsWith(")F")) {
                        mi.setOpcode(INVOKESTATIC);
                        mi.owner = METHODS;
                        mi.name = "sanitizeHookHealth";
                        mi.desc = "(Ljava/lang/Object;FLjava/lang/Object;Ljava/lang/Object;)F";
                        mi.itf = false;
                        modified = true;
                    } else if (d.startsWith("(ZLjava/lang/Object;") && d.endsWith(")Z")) {
                        String n = mi.name.toLowerCase();
                        String target = n.contains("dead") ? "sanitizeHookDeadOrDying" :
                                n.contains("removed") ? "sanitizeHookRemoved" : "sanitizeHookAlive";
                        mi.setOpcode(INVOKESTATIC);
                        mi.owner = METHODS;
                        mi.name = target;
                        mi.desc = "(Ljava/lang/Object;ZLjava/lang/Object;Ljava/lang/Object;)Z";
                        mi.itf = false;
                        modified = true;
                    }
                }
            }
        }

        return modified ? ILaunchPluginService.ComputeFlags.SIMPLE_REWRITE : 0;
    }

    private static void injectForceTrue(MethodNode method) {
        LabelNode skip = new LabelNode(new Label());
        InsnList list = new InsnList();
        list.add(new VarInsnNode(ALOAD, 0));
        list.add(new MethodInsnNode(INVOKESTATIC, METHODS, "shouldForceAttackable", "(Ljava/lang/Object;)Z", false));
        list.add(new JumpInsnNode(IFEQ, skip));
        list.add(new InsnNode(ICONST_1));
        list.add(new InsnNode(IRETURN));
        list.add(skip);
        list.add(new FrameNode(F_SAME, 0, null, 0, null));
        method.instructions.insertBefore(method.instructions.getFirst(), list);
        method.maxStack = Math.max(method.maxStack, 2);
    }

    private static boolean isExcludedOwner(String owner) {
        if (owner == null) return true;
        return owner.startsWith("java/util/concurrent/") ||
                owner.startsWith("io/netty/") ||
                owner.contains("audio") ||
                owner.contains("sound") ||
                owner.startsWith("net/minecraft/client/gui/") ||
                owner.startsWith("net/minecraft/world/inventory/");
    }

    private static boolean isTargetEntity(String owner) {
        if (isExcludedOwner(owner)) return false;
        return owner.equals("net/minecraft/world/entity/Entity") ||
                owner.equals("net/minecraft/world/entity/LivingEntity") ||
                owner.equals("net/minecraft/world/entity/Mob") ||
                owner.equals("net/minecraft/world/entity/player/Player") ||
                owner.equals("net/minecraft/server/level/ServerPlayer") ||
                owner.startsWith("net/minecraft/client/player/") ||
                (owner.endsWith("Entity") && !owner.contains("BlockEntity")) ||
                owner.endsWith("Player");
    }

    private static boolean isSys(String o) {
        return o.startsWith("java/") || o.startsWith("net/minecraft/") || o.startsWith("net/minecraftforge/") || o.startsWith("com/maxwell/hyperdamagelib/");
    }
}