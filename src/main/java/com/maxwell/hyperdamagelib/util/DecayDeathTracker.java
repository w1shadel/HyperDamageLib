package com.maxwell.hyperdamagelib.util;

import com.maxwell.hyperdamagelib.HDL;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.io.PrintWriter;
import java.io.StringWriter;

public class DecayDeathTracker {
    public static void logHurt(LivingEntity entity, DamageSource source, float amount, boolean isInvincible) {
        if (!(entity instanceof Player)) return;
        String srcType = (source != null && source.typeHolder() != null) ? source.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("unknown") : "null";
        String attacker = (source != null && source.getEntity() != null) ? source.getEntity().toString() : "none";
        HDL.LOGGER.warn("================================================================================");
        HDL.LOGGER.warn("[HDL-FATAL-TRACKER] HURT DETECTED on Player: {}", entity.getName().getString());
        HDL.LOGGER.warn("  - Damage Amount: {}", amount);
        HDL.LOGGER.warn("  - Damage Type: {}", srcType);
        HDL.LOGGER.warn("  - Attacker: {}", attacker);
        HDL.LOGGER.warn("  - Is Invincible at this moment: {}", isInvincible);
        HDL.LOGGER.warn("  - Call Stack (Who initiated this attack?):");
        printFilteredStackTrace();
        HDL.LOGGER.warn("================================================================================");
    }

    public static void logSetHealth(LivingEntity entity, float oldHealth, float newHealth, boolean isInvincible) {
        if (!(entity instanceof Player)) return;
        if (newHealth >= oldHealth) return;
        HDL.LOGGER.warn("================================================================================");
        HDL.LOGGER.warn("[HDL-FATAL-TRACKER] HEALTH DRAIN on Player: {}", entity.getName().getString());
        HDL.LOGGER.warn("  - Health Change: {} -> {}", oldHealth, newHealth);
        HDL.LOGGER.warn("  - Is Invincible at this moment: {}", isInvincible);
        HDL.LOGGER.warn("  - Call Stack (Who changed the health?):");
        printFilteredStackTrace();
        HDL.LOGGER.warn("================================================================================");
    }

    public static void logDie(LivingEntity entity, DamageSource source, boolean isInvincible) {
        if (!(entity instanceof Player)) return;
        String srcType = (source != null && source.typeHolder() != null) ? source.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("unknown") : "null";
        HDL.LOGGER.error("################################################################################");
        HDL.LOGGER.error("[HDL-FATAL-TRACKER] DIE() INVOCATION on Player: {}", entity.getName().getString());
        HDL.LOGGER.error("  - Final Death Source: {}", srcType);
        HDL.LOGGER.error("  - Is Invincible at this moment: {}", isInvincible);
        HDL.LOGGER.error("  - Exact StackTrace of Killer (Root Cause):");
        printFilteredStackTrace();
        HDL.LOGGER.error("################################################################################");
    }

    public static void logClientDeath(String reason, boolean isInvincible) {
        HDL.LOGGER.error("################################################################################");
        HDL.LOGGER.error("[HDL-FATAL-TRACKER] CLIENT-SIDE DEATH TRIGGERED! Reason: {}", reason);
        HDL.LOGGER.error("  - Is Client Invincible: {}", isInvincible);
        HDL.LOGGER.error("  - Client Call Stack:");
        printFilteredStackTrace();
        HDL.LOGGER.error("################################################################################");
    }

    private static void printFilteredStackTrace() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        for (int i = 3; i < Math.min(stack.length, 25); i++) {
            pw.println("      at " + stack[i].toString());
        }
        HDL.LOGGER.warn(sw.toString());
    }
}