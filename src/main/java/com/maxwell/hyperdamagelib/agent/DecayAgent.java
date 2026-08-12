package com.maxwell.hyperdamagelib.agent;

import com.maxwell.hyperdamagelib.util.DecaySecurity;

import java.lang.instrument.Instrumentation;
import java.util.logging.Logger;

public class DecayAgent {
    private static final Logger LOGGER = Logger.getLogger("DecayAgent");
    private static volatile Instrumentation INSTRUMENTATION;
    private static volatile boolean LOCKED = false;

    static {
        LOGGER.info("Decay Agent Class Loaded");
    }

    public static Instrumentation getInstrumentation() {
        DecaySecurity.checkReflectionAccess();
        if (!isCallerAuthorized()) {
            throw new SecurityException("[HDL Security] Unauthorized getInstrumentation call!");
        }
        return INSTRUMENTATION;
    }

    public static void setInstrumentation(Instrumentation inst) {
        DecaySecurity.checkReflectionAccess();
        if (LOCKED) {
            throw new SecurityException("[HDL Security] DecayAgent.INSTRUMENTATION is already locked!");
        }
        if (!isCallerAuthorized()) {
            throw new SecurityException("[HDL Security] Unauthorized setInstrumentation call!");
        }
        INSTRUMENTATION = inst;
        LOCKED = true;
    }

    private static boolean isCallerAuthorized() {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        return walker.walk(frames ->
                frames.map(StackWalker.StackFrame::getDeclaringClass)
                        .map(Class::getName)
                        .anyMatch(className -> className.startsWith("com.maxwell.hyperdamagelib"))
        );
    }

    public static void premain(String args, Instrumentation inst) {
        setInstrumentation(inst);
        LOGGER.info("Decay Agent premain initialized");
    }

    public static void agentmain(String args, Instrumentation inst) {
        setInstrumentation(inst);
        LOGGER.info("Decay Agent agentmain initialized");
    }
}