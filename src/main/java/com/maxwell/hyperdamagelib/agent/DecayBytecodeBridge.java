package com.maxwell.hyperdamagelib.agent;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class DecayBytecodeBridge {
    private static final Logger LOGGER = Logger.getLogger("DecayBytecodeBridge");
    public static volatile BiFunction<Optional<byte[]>, String, Optional<byte[]>> transformer;

    private DecayBytecodeBridge() {}

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