package com.maxwell.hyperdamagelib.transformer;

import com.maxwell.hyperdamagelib.HDL;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DecayBytecodeDebugger {
    private static final Path DUMP_DIR = Paths.get("dumps", "hdl_transformed_classes");

    public static void dumpMethodInstructions(String className, MethodNode method, String label) {
        Printer printer = new Textifier();
        TraceMethodVisitor mp = new TraceMethodVisitor(printer);
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("================================================================================");
        pw.println("[HDL-DEBUG] " + label + " | Class: " + className + " | Method: " + method.name + method.desc);
        pw.println("================================================================================");
        for (int i = 0; i < method.instructions.size(); i++) {
            AbstractInsnNode insn = method.instructions.get(i);
            insn.accept(mp);
            String instructionText = printer.getText().isEmpty() ? "" : printer.getText().get(0).toString().trim();
            pw.printf("  [%03d] %s%n", i, instructionText);
            printer.getText().clear();
        }
        pw.println("================================================================================");
        HDL.LOGGER.info(sw.toString());
    }

    public static void dumpClassBytes(String className, byte[] classBytes) {
        if (classBytes == null || classBytes.length == 0) return;
        try {
            Path classPath = DUMP_DIR.resolve(className.replace('/', File.separatorChar) + ".class");
            Files.createDirectories(classPath.getParent());
            try (FileOutputStream fos = new FileOutputStream(classPath.toFile())) {
                fos.write(classBytes);
            }
            HDL.LOGGER.info("[HDL-DEBUG] Dumped transformed class to: {}", classPath.toAbsolutePath());
        } catch (Throwable t) {
            HDL.LOGGER.error("[HDL-DEBUG] Failed to dump class bytes for " + className, t);
        }
    }
}