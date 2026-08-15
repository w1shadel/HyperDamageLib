package com.maxwell.hyperdamagelib.transformer;


import com.maxwell.hyperdamagelib.HDL;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

class HierarchyAwareClassWriter extends ClassWriter {
    private final ClassLoader loader;
    private final ClassNode currentClass;

    HierarchyAwareClassWriter(ClassNode classNode, int flags) {
        super(flags);
        this.currentClass = classNode;
        this.loader = Thread.currentThread().getContextClassLoader();
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        try {
            if (type1.equals(type2)) return type1;
            if ("java/lang/Object".equals(type1) || "java/lang/Object".equals(type2)) return "java/lang/Object";

            Set<String> ancestors = new HashSet<>();
            String c = type1;
            while (c != null) {
                ancestors.add(c);
                if ("java/lang/Object".equals(c)) break;
                c = readSuperName(c);
            }
            ancestors.add("java/lang/Object");

            c = type2;
            while (c != null) {
                if (ancestors.contains(c)) return c;
                if ("java/lang/Object".equals(c)) break;
                c = readSuperName(c);
            }
            return "java/lang/Object";
        } catch (Throwable t) {
            HDL.LOGGER.warn("getCommonSuperClass({}, {}) failed: {} -- defaulting to java/lang/Object",
                    type1, type2, t.toString());
            return "java/lang/Object";
        }
    }

    private String readSuperName(String name) throws IOException {
        if (currentClass != null && name.equals(currentClass.name)) {
            return currentClass.superName != null ? currentClass.superName : "java/lang/Object";
        }
        try (InputStream is = loader.getResourceAsStream(name + ".class")) {
            if (is == null) {
                return "java/lang/Object";
            }
            String s = new ClassReader(is).getSuperName();
            return s != null ? s : "java/lang/Object";
        }
    }
}