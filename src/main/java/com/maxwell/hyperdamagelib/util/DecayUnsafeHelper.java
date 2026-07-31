package com.maxwell.hyperdamagelib.util;

import sun.misc.Unsafe;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.Random;

public class DecayUnsafeHelper {
    public static final Unsafe UNSAFE;
    public static final boolean AVAILABLE;
    public static final long OVERRIDE_OFFSET = 12;
    private static final Random RANDOM = new Random();

    private static final String[] FUNNY_DESCRIPTIONS = {
            "実績達成: 侮る葛に倒さる",
    };
    private static final String[] FUNNY_THROWABLES = {
            "良く分からない方法でアクセスしようとして大失敗する。",
    };

    public static void detectAndCrashOnReflection() {
        for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
            String className = element.getClassName();

            if (className.equals("java.lang.reflect.Method") ||
                    className.startsWith("sun.reflect.") ||
                    className.startsWith("jdk.internal.reflect.")) {

                String randomDescription = FUNNY_DESCRIPTIONS[RANDOM.nextInt(FUNNY_DESCRIPTIONS.length)];
                String randomThrowableMsg = FUNNY_THROWABLES[RANDOM.nextInt(FUNNY_THROWABLES.length)] + " | Caller: " + element.toString();

                Throwable throwable = new SecurityException(randomThrowableMsg);

                net.minecraft.CrashReport crashReport = new net.minecraft.CrashReport(randomDescription, throwable);

                net.minecraft.CrashReportCategory category = crashReport.addCategory("HDL Security Details");
                category.setDetail("Unauthorized Caller Class", className);
                category.setDetail("Stack Trace Element", element.toString());
                category.setDetail("Security Action", "Triggered native crash to protect game state.");

                if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
                    try {
                        triggerClientCrash(crashReport);
                    } catch (Throwable ignored) {
                    }
                }

                throw new net.minecraft.ReportedException(crashReport);
            }
        }
    }

    private static void triggerClientCrash(net.minecraft.CrashReport crashReport) {
        net.minecraft.client.Minecraft.getInstance().delayCrash(crashReport);
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