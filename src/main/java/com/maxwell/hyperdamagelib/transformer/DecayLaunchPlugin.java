package com.maxwell.hyperdamagelib.transformer;

import cpw.mods.modlauncher.api.ITransformerActivity;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.util.EnumSet;

public class DecayLaunchPlugin implements ILaunchPluginService {
    @Override
    public String name() {
        return "decay_plugin";
    }

    @Override
    public EnumSet<Phase> handlesClass(Type type, boolean b) {
        if (type.getClassName().startsWith("com.maxwell.hyperdamagelib.transformer"))
            return EnumSet.noneOf(Phase.class);
        return EnumSet.of(Phase.AFTER, Phase.BEFORE);
    }

    @Override
    public boolean processClass(Phase phase, ClassNode classNode, Type classType, String reason) {
        if (classNode.name.startsWith("com/maxwell/hyperdamagelib/transformer"))
            return false;
        if (reason.equals(ITransformerActivity.CLASSLOADING_REASON)) {
            return DecayGenericTransformer.transform(phase == Phase.AFTER ? DecayGenericTransformer.Phase.ILaunchPluginService : DecayGenericTransformer.Phase.ILaunchPluginServiceBefore, classNode);
        }
        return false;
    }
}