package com.maxwell.hyperdamagelib.util;

public final class DecaySecurity {

    
    public static void checkReflectionAccess() {
        if (isInvokedViaReflection()) {
            throw new SecurityException("Access denied: Dynamic reflection is prohibited on this method.");
        }
    }

    private static boolean isInvokedViaReflection() {
        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
        return walker.walk(frames ->
                frames.map(StackWalker.StackFrame::getDeclaringClass)
                        .map(Class::getName)
                        .anyMatch(DecaySecurity::isReflectionClass)
        );
    }

    private static boolean isReflectionClass(String className) {
        return className.startsWith("java.lang.reflect.")
                || className.startsWith("java.lang.invoke.")
                || className.startsWith("jdk.internal.reflect.")
                || className.startsWith("sun.reflect.");
    }
}