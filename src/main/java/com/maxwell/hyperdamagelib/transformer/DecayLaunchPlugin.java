package com.maxwell.hyperdamagelib.transformer;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.EnumSet;

public class DecayLaunchPlugin implements ILaunchPluginService {
    @Override
    public String name() {
        return "decay_launch_plugin";
    }

    @Override
    public EnumSet<Phase> handlesClass(Type type, boolean isEmpty) {
        String className = type.getClassName();
        if (className.contains(".mixin.") ||
                className.contains(".mixins.") ||
                className.endsWith("Mixin") ||
                className.endsWith("Accessor") ||
                className.endsWith("Invoker") ||
                className.startsWith("com.maxwell.hyperdamagelib") ||
                className.startsWith("cpw.mods") ||
                className.startsWith("net.minecraftforge.registries") ||
                className.startsWith("org.spongepowered")) {
            return EnumSet.noneOf(Phase.class);
        }
        return EnumSet.of(Phase.AFTER);
    }

    @Override
    public int processClassWithFlags(Phase phase, ClassNode classNode, Type classType, String reason) {
        if (classNode.name.startsWith("com/maxwell/hyperdamagelib") ||
                classNode.name.contains("/mixin/") ||
                classNode.name.contains("/mixins/") ||
                classNode.name.endsWith("Mixin") ||
                classNode.name.startsWith("net/minecraftforge/registries")) {
            return ComputeFlags.NO_REWRITE;
        }
        boolean modified = false;
        try {
            for (MethodNode method : classNode.methods) {
                modified |= DecaySurgicalStripper.stripForeignHooks(classNode.name, method);
            }
            modified |= DecayGenericTransformer.transform(DecayGenericTransformer.Phase.ILaunchPluginService, classNode);

        } catch (Throwable t) {
            return ComputeFlags.NO_REWRITE;
        }
        return modified ? ComputeFlags.SIMPLE_REWRITE : ComputeFlags.NO_REWRITE;
    }
}