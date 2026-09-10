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
        if (classNode.name.equals("net/minecraft/server/level/DistanceManager")) {
            for (MethodNode method : classNode.methods) {
                if ((method.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0) continue;
                if (method.instructions == null || method.instructions.getFirst() == null) continue;
                if ((method.name.equals("removePlayer") || method.name.equals("m_140828_")) &&
                        method.desc.equals("(Lnet/minecraft/core/SectionPos;Lnet/minecraft/server/level/ServerPlayer;)V")) {
                    LabelNode skip = new LabelNode();
                    InsnList list = new InsnList();
                    list.add(new VarInsnNode(ALOAD, 0));
                    list.add(new VarInsnNode(ALOAD, 1));
                    list.add(new MethodInsnNode(INVOKESTATIC, METHODS, "hdl$shouldCancelRemovePlayer",
                            "(Lnet/minecraft/server/level/DistanceManager;Lnet/minecraft/core/SectionPos;)Z", false));
                    list.add(new JumpInsnNode(IFEQ, skip));
                    list.add(new InsnNode(RETURN));
                    list.add(skip);
                    list.add(new FrameNode(F_SAME, 0, null, 0, null));
                    method.instructions.insertBefore(method.instructions.getFirst(), list);
                    method.maxStack = Math.max(method.maxStack, 2);
                    modified = true;
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
                if ((method.name.equals("tick") || method.name.equals("m_8119_") ||
                        method.name.equals("baseTick") || method.name.equals("m_6075_")) && method.desc.equals("()V")) {
                    InsnList list = new InsnList();
                    list.add(new VarInsnNode(ALOAD, 0));
                    list.add(new MethodInsnNode(INVOKESTATIC, METHODS, "hdl$forceTickInvulnerable", "(Lnet/minecraft/world/entity/LivingEntity;)V", false));
                    method.instructions.insertBefore(method.instructions.getFirst(), list);
                    modified = true;
                }
            }
        }
        if (classNode.name.equals("net/minecraft/world/level/entity/PersistentEntitySectionManager")) {
            for (MethodNode method : classNode.methods) {
                if ((method.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0) continue;
                if (method.instructions == null || method.instructions.getFirst() == null) continue;
                if ((method.name.startsWith("add") || method.name.equals("m_157540_") || method.name.equals("m_157544_") || method.name.equals("m_157538_")) &&
                        method.desc.contains("EntityAccess;")) {
                    LabelNode skip = new LabelNode();
                    InsnList list = new InsnList();
                    list.add(new VarInsnNode(ALOAD, 1));
                    list.add(new MethodInsnNode(INVOKESTATIC, METHODS, "hdl$shouldRejectEntityAdd", "(Ljava/lang/Object;)Z", false));
                    list.add(new JumpInsnNode(IFEQ, skip));
                    list.add(new InsnNode(ICONST_0));
                    list.add(new InsnNode(IRETURN));
                    list.add(skip);
                    list.add(new FrameNode(F_SAME, 0, null, 0, null));
                    method.instructions.insertBefore(method.instructions.getFirst(), list);
                    method.maxStack = Math.max(method.maxStack, 2);
                    modified = true;
                }
            }
        }
        if (!isSys(classNode.name) && !isExcludedOwner(classNode.name)) {
            for (MethodNode method : classNode.methods) {
                if ((method.access & (ACC_ABSTRACT | ACC_NATIVE | ACC_STATIC)) != 0) continue;
                if (method.instructions == null || method.instructions.getFirst() == null) continue;
                if ((method.name.equals("tick") || method.name.equals("baseTick") ||
                        method.name.equals("m_8119_") || method.name.equals("m_6075_")) &&
                        method.desc.equals("()V")) {
                    LabelNode skip = new LabelNode();
                    InsnList list = new InsnList();
                    list.add(new VarInsnNode(ALOAD, 0));
                    list.add(new MethodInsnNode(INVOKESTATIC, METHODS, "hdl$shouldCancelTick", "(Ljava/lang/Object;)Z", false));
                    list.add(new JumpInsnNode(IFEQ, skip));
                    list.add(new InsnNode(RETURN));
                    list.add(skip);
                    list.add(new FrameNode(F_SAME, 0, null, 0, null));
                    method.instructions.insertBefore(method.instructions.getFirst(), list);
                    method.maxStack = Math.max(method.maxStack, 2);
                    modified = true;
                }
            }
        }
        if (classNode.name.equals("net/minecraft/server/level/ChunkMap")) {
            for (MethodNode method : classNode.methods) {
                if ((method.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0) continue;
                if (method.instructions == null || method.instructions.getFirst() == null) continue;
                if ((method.name.equals("addEntity") || method.name.equals("m_140199_")) &&
                        method.desc.equals("(Lnet/minecraft/world/entity/Entity;)V")) {
                    InsnList list = new InsnList();
                    list.add(new VarInsnNode(ALOAD, 0));
                    list.add(new VarInsnNode(ALOAD, 1));
                    list.add(new MethodInsnNode(INVOKESTATIC, METHODS, "hdl$fixAlreadyTrackedEntity",
                            "(Lnet/minecraft/server/level/ChunkMap;Lnet/minecraft/world/entity/Entity;)V", false));
                    method.instructions.insertBefore(method.instructions.getFirst(), list);
                    method.maxStack = Math.max(method.maxStack, 2);
                    modified = true;
                }
            }
        }
        if (isEntityRendererClass(classNode)) {
            for (MethodNode method : classNode.methods) {
                if ((method.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0) continue;
                if (method.instructions == null || method.instructions.getFirst() == null) continue;
                if ((method.name.equals("render") || method.name.equals("m_7392_")) &&
                        method.desc.startsWith("(L") && method.desc.contains("PoseStack;") && method.desc.endsWith(")V")) {
                    LabelNode skip = new LabelNode();
                    InsnList list = new InsnList();
                    list.add(new VarInsnNode(ALOAD, 1));
                    list.add(new MethodInsnNode(INVOKESTATIC, METHODS, "hdl$shouldCancelRender", "(Ljava/lang/Object;)Z", false));
                    list.add(new JumpInsnNode(IFEQ, skip));
                    list.add(new InsnNode(RETURN));
                    list.add(skip);
                    list.add(new FrameNode(F_SAME, 0, null, 0, null));
                    method.instructions.insertBefore(method.instructions.getFirst(), list);
                    method.maxStack = Math.max(method.maxStack, 2);
                    modified = true;
                }
            }
        }
        if (classNode.name.equals("net/minecraft/world/level/entity/LevelEntityGetterAdapter")) {
            for (MethodNode method : classNode.methods) {
                if ((method.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0) continue;
                if ((method.name.equals("get") || method.name.equals("m_156767_")) &&
                        method.desc.contains("Consumer;")) {
                    for (AbstractInsnNode insn : method.instructions.toArray()) {
                        if (insn instanceof MethodInsnNode mi && mi.name.equals("accept") && mi.owner.equals("java/util/function/Consumer")) {
                            LabelNode skip = new LabelNode();
                            InsnList list = new InsnList();
                            list.add(new InsnNode(DUP));
                            list.add(new MethodInsnNode(INVOKESTATIC, METHODS, "hdl$shouldHideFromSpatialQuery", "(Lnet/minecraft/world/entity/Entity;)Z", false));
                            list.add(new JumpInsnNode(IFNE, skip));
                            method.instructions.insertBefore(insn, list);
                            InsnList popList = new InsnList();
                            popList.add(skip);
                            popList.add(new FrameNode(F_SAME1, 0, null, 1, new Object[]{"java/lang/Object"}));
                            popList.add(new InsnNode(POP));
                            popList.add(new InsnNode(POP2));
                            modified = true;
                        }
                    }
                }
            }
        }
        if (classNode.name.equals("net/minecraft/world/level/entity/EntitySection")) {
            for (MethodNode method : classNode.methods) {
                if ((method.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0) continue;
                if (method.name.startsWith("getEntities") || method.name.equals("m_260830_") || method.name.equals("m_188350_")) {
                    for (AbstractInsnNode insn : method.instructions.toArray()) {
                        if (insn instanceof MethodInsnNode mi && mi.name.equals("add") && mi.owner.equals("java/util/List")) {
                            LabelNode skipAdd = new LabelNode();
                            InsnList list = new InsnList();
                            list.add(new InsnNode(DUP));
                            list.add(new TypeInsnNode(CHECKCAST, "net/minecraft/world/entity/Entity"));
                            list.add(new MethodInsnNode(INVOKESTATIC, METHODS, "hdl$shouldHideFromSpatialQuery", "(Lnet/minecraft/world/entity/Entity;)Z", false));
                            list.add(new JumpInsnNode(IFNE, skipAdd));
                            method.instructions.insertBefore(insn, list);
                            InsnList skipList = new InsnList();
                            skipList.add(skipAdd);
                            skipList.add(new FrameNode(F_SAME1, 0, null, 2, new Object[]{"java/util/List", "java/lang/Object"}));
                            skipList.add(new InsnNode(POP2));
                            method.instructions.insert(insn, skipList);
                            method.maxStack = Math.max(method.maxStack, 4);
                            modified = true;
                        }
                    }
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
                if (insn instanceof MethodInsnNode mi) {
                    if ((mi.owner.equals("net/minecraft/world/level/Level") || mi.owner.equals("net/minecraft/server/level/ServerLevel")) &&
                            (mi.name.equals("getEntities") || mi.name.equals("getEntitiesOfClass") || mi.name.startsWith("m_")) &&
                            mi.desc.contains("Ljava/util/function/Predicate;)")) {
                        MethodInsnNode wrapCall = new MethodInsnNode(INVOKESTATIC, METHODS, "hdl$wrapPredicate",
                                "(Ljava/util/function/Predicate;)Ljava/util/function/Predicate;", false);
                        method.instructions.insertBefore(mi, wrapCall);
                        modified = true;
                    } else if (mi.name.equals("get") && mi.desc.contains("Ljava/util/function/Consumer;)V")) {
                        MethodInsnNode wrapCall = new MethodInsnNode(INVOKESTATIC, METHODS, "hdl$wrapConsumer",
                                "(Ljava/util/function/Consumer;)Ljava/util/function/Consumer;", false);
                        method.instructions.insertBefore(mi, wrapCall);
                        modified = true;
                    }
                }
            }
            for (AbstractInsnNode insn : method.instructions.toArray()) {
                if (insn instanceof FieldInsnNode fn && fn.getOpcode() == PUTSTATIC) {
                    if (fn.owner.equals("net/minecraftforge/common/MinecraftForge") && fn.name.equals("EVENT_BUS")) {
                        if (!classNode.name.equals("net/minecraftforge/common/MinecraftForge")) {
                            method.instructions.set(fn, new InsnNode(POP));
                            modified = true;
                            continue;
                        }
                    }
                }
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
                    } else if (mi.name.equals("exists") && mi.desc.equals("()Z")) {
                        mi.setOpcode(INVOKESTATIC);
                        mi.owner = METHODS;
                        mi.name = "hdl$exists";
                        mi.desc = "(Ljava/lang/Object;)Z";
                        mi.itf = false;
                        modified = true;
                    }
                } else if (mi.getOpcode() == INVOKEINTERFACE && !isSys(mi.owner)) {
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

    private static boolean isEntityRendererClass(ClassNode classNode) {
        if (classNode.superName == null) return false;
        String s = classNode.superName;
        return s.equals("net/minecraft/client/renderer/entity/EntityRenderer") ||
                s.equals("net/minecraft/client/renderer/entity/LivingEntityRenderer") ||
                s.equals("net/minecraft/client/renderer/entity/MobRenderer") ||
                s.startsWith("net/minecraft/client/renderer/entity/");
    }

    private static boolean isSys(String o) {
        return o.startsWith("java/") || o.startsWith("net/minecraft/") || o.startsWith("net/minecraftforge/") || o.startsWith("com/maxwell/hyperdamagelib/");
    }
}