package com.maxwell.hyperdamagelib.transformer;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.util.EnumSet;

public class DecayLaunchPlugin implements ILaunchPluginService {
    @Override
    public String name() {
        return "hyperdamagelib_plugin";
    }

    @Override
    public EnumSet<Phase> handlesClass(Type type, boolean isEmpty) {
        String name = type.getClassName();
        if (name.startsWith("com.maxwell.hyperdamagelib.transformer.") ||
                name.startsWith("cpw.mods.modlauncher.") ||
                name.startsWith("java.") ||
                name.startsWith("jdk.") ||
                name.startsWith("sun.")) {
            return EnumSet.noneOf(Phase.class);
        }
        return EnumSet.of(Phase.AFTER);
    }

    @Override
    public int processClassWithFlags(Phase phase, ClassNode classNode, Type classType, String reason) {
        if (classNode.name.startsWith("com/maxwell/hyperdamagelib/transformer/")) {
            return ComputeFlags.NO_REWRITE;
        }
        if (phase == Phase.AFTER) {
            try {
                int result = DecayGenericTransformer.transform(classNode);
                return result != 0 ? result : ComputeFlags.NO_REWRITE;
            } catch (Throwable t) {
                com.maxwell.hyperdamagelib.HDL.LOGGER.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                com.maxwell.hyperdamagelib.HDL.LOGGER.error("[HDL-CRITICAL-FAIL] Transformation crashed on: " + classNode.name, t);
                com.maxwell.hyperdamagelib.HDL.LOGGER.error("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                return ComputeFlags.NO_REWRITE;
            }
        }
        return ComputeFlags.NO_REWRITE;
    }
}