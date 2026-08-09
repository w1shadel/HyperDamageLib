package com.maxwell.hyperdamagelib.util;

import sun.misc.Unsafe;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.Random;

public class DecayUnsafeHelper {
    public static final Unsafe UNSAFE;
    public static final boolean AVAILABLE;
    public static final long OVERRIDE_OFFSET = 12;

    public static void detectAndCrashOnReflection() {
        if (isInvokedViaReflection()) {
            throw new SecurityException("Access denied: Direct compiled calls are required. Reflection is prohibited.");
        }
    }

    private static boolean isInvokedViaReflection() {

        StackWalker walker = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

        return walker.walk(frames ->
                frames.map(StackWalker.StackFrame::getDeclaringClass)
                        .map(Class::getName)

                        .anyMatch(DecayUnsafeHelper::isReflectionClass)
        );
    }

    /**
     * 指定されたクラス名がリフレクション用の内部クラスかどうか判定します。
     */
    private static boolean isReflectionClass(String className) {
        return className.startsWith("java.lang.reflect.")      
                || className.startsWith("java.lang.invoke.")       
                || className.startsWith("jdk.internal.reflect.")   
                || className.startsWith("sun.reflect.");           
    }



    static {
        Unsafe unsafe = null;
        boolean available = false;
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            unsafe = (Unsafe) f.get(null);
            available = true;
        } catch (Exception ignored) {
        }
        UNSAFE = unsafe;
        AVAILABLE = available;
    }

    public static boolean allowAttachSelf() {
        if (!AVAILABLE) return false;
        try {
            Class<?> clazz = Class.forName("sun.tools.attach.HotSpotVirtualMachine");
            for (Field f : clazz.getDeclaredFields()) {
                if (f.getType() == boolean.class && f.getName().equals("ALLOW_ATTACH_SELF")) {
                    long offset = UNSAFE.staticFieldOffset(f);
                    Object base = UNSAFE.staticFieldBase(f);
                    UNSAFE.putBoolean(base, offset, true);
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static boolean forceSetAccessible(AccessibleObject accessibleObject) {
        detectAndCrashOnReflection();
        if (accessibleObject.trySetAccessible()) {
            return true;
        }
        if (!AVAILABLE) {
            return false;
        }
        try {
            UNSAFE.putBoolean(accessibleObject, OVERRIDE_OFFSET, true);
            return true;
        } catch (Throwable throwable) {
            return false;
        }
    }

    public static long getFieldOffset(Field field) {
        if (!AVAILABLE) return -1;
        try {
            return UNSAFE.objectFieldOffset(field);
        } catch (Throwable e) {
            return -1;
        }
    }

    public static boolean putBoolean(Object object, long offset, boolean value) {
        if (!AVAILABLE || offset == -1) return false;
        try {
            UNSAFE.putBoolean(object, offset, value);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    public static boolean putObject(Object object, long offset, Object value) {
        if (!AVAILABLE || offset == -1) return false;
        try {
            UNSAFE.putObject(object, offset, value);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }
}