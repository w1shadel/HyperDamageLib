package com.maxwell.hyperdamagelib.transformer;

import cpw.mods.modlauncher.api.ITransformerActivity;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.util.EnumSet;

public class DecayLaunchPlugin implements ILaunchPluginService {

    @Override
    public String name() {
        return "decay_launch_plugin";
    }

    @Override
    public EnumSet<Phase> handlesClass(Type type, boolean isEmpty) {
        if (type.getClassName().startsWith("com.maxwell.hyperdamagelib.transformer") ||
                type.getClassName().startsWith("com.maxwell.hyperdamagelib.agent")) {
            return EnumSet.noneOf(Phase.class);
        }
        return EnumSet.of(Phase.AFTER, Phase.BEFORE);
    }

    @Override
    public int processClassWithFlags(Phase phase, ClassNode classNode, Type classType, String reason) {
        if (classNode.name.startsWith("com/maxwell/hyperdamagelib/transformer") ||
                classNode.name.startsWith("com/maxwell/hyperdamagelib/agent")) {
            return ComputeFlags.NO_REWRITE;
        }

        DecayGenericTransformer.Phase tPhase = (phase == Phase.AFTER)
                ? DecayGenericTransformer.Phase.ILaunchPluginService
                : DecayGenericTransformer.Phase.ILaunchPluginServiceBefore;

        boolean modified = DecayGenericTransformer.transform(tPhase, classNode);
        return modified ? ComputeFlags.SIMPLE_REWRITE : ComputeFlags.NO_REWRITE;
    }
}