package com.maxwell.hyperdamagelib.agent;

import com.maxwell.hyperdamagelib.util.DecaySecurity;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DecayBytecodeBridge {
    private static final Logger LOGGER = Logger.getLogger("DecayBytecodeBridge");
    private static volatile BiFunction<Optional<byte[]>, String, Optional<byte[]>> transformer;
    private static volatile boolean LOCKED = false;

    private DecayBytecodeBridge() {
    }

    public static void setTransformer(BiFunction<Optional<byte[]>, String, Optional<byte[]>> t) {
        DecaySecurity.checkReflectionAccess();
        if (LOCKED) {
            throw new SecurityException("[HDL Security] DecayBytecodeBridge is already locked!");
        }
        if (!isCallerAuthorized()) {
            throw new SecurityException("[HDL Security] Unauthorized access to DecayBytecodeBridge detected!");
        }
        transformer = t;
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

    public static Optional<byte[]> transformOptionalBytes(Optional<byte[]> bytes, String className) {
        BiFunction<Optional<byte[]>, String, Optional<byte[]>> t = transformer;
        if (t == null || bytes == null) return bytes;
        try {
            return t.apply(bytes, className);
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "DecayBytecodeBridge transformation failed for: " + className, e);
            return bytes;
        }
    }
}