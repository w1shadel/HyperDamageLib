package com.maxwell.hyperdamagelib.util;

import sun.misc.Unsafe;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;

public class DecaySecurityHelper {
    public static final Unsafe UNSAFE;
    public static final boolean AVAILABLE;
    public static final long OVERRIDE_OFFSET = 12;

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

    public static boolean forceSetAccessible(AccessibleObject accessibleObject) {
        DecaySecurity.checkReflectionAccess();
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
}