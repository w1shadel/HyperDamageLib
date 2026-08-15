package com.maxwell.hyperdamagelib.agent;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class DecayBytecodeBridge {
    private static final Logger LOGGER = Logger.getLogger("DecayBytecodeBridge");
    public static volatile BiFunction<Optional<byte[]>, String, Optional<byte[]>> transformer;
    public static volatile BiFunction<Stream<byte[]>, String, Stream<byte[]>> streamTransformer;

    private DecayBytecodeBridge() {}

    public static Optional<byte[]> transformOptionalBytes(Optional<byte[]> bytes, String className) {
        BiFunction<Optional<byte[]>, String, Optional<byte[]>> t = transformer;
        if (t == null || bytes == null) return bytes;
        try {
            return t.apply(bytes, className);
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "DecayBytecodeBridge.transformOptionalBytes failed for " + className, e);
            return bytes;
        }
    }

    public static Stream<byte[]> transformStreamBytes(Stream<byte[]> bytes, String className) {
        BiFunction<Stream<byte[]>, String, Stream<byte[]>> t = streamTransformer;
        if (t == null || bytes == null) return bytes;
        try {
            return t.apply(bytes, className);
        } catch (Throwable e) {
            LOGGER.log(Level.WARNING, "DecayBytecodeBridge.transformStreamBytes failed for " + className, e);
            return bytes;
        }
    }
}