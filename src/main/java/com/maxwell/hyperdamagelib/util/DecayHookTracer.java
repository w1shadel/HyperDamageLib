package com.maxwell.hyperdamagelib.util;

import com.maxwell.hyperdamagelib.HDL;
import net.minecraft.world.entity.Entity;

import java.lang.StackWalker.StackFrame;
import java.util.List;
import java.util.stream.Collectors;

public final class DecayHookTracer {
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);
    private static long lastTraceTime = 0;

    public static void traceCall(String methodName, Entity entity, Object incomingValue) {
        long now = System.currentTimeMillis();
        if (now - lastTraceTime < 10000) return;
        lastTraceTime = now;
        List<StackFrame> frames = WALKER.walk(s -> s.limit(15).collect(Collectors.toList()));
        StringBuilder sb = new StringBuilder();
        sb.append("\n§d================ [HDL 動的フック監視: ").append(methodName).append("] ================\n");
        sb.append("§e対象エンティティ: §f").append(entity != null ? entity.getName().getString() : "null");
        sb.append(" (UUID: ").append(entity != null ? entity.getUUID() : "null").append(")\n");
        sb.append("§e渡されたスタック値: §b").append(incomingValue).append("\n");
        sb.append("§a[呼び出しコールスタック (実行された順番)]:\n");
        int depth = 0;
        for (StackFrame frame : frames) {
            String className = frame.getClassName();
            String mName = frame.getMethodName();
            int line = frame.getLineNumber();
            if (className.contains("DecayHookTracer")) continue;
            String color = "§7";
            String tag = "";
            if (className.contains("nosugar")) {
                color = "§c";
                tag = " [★NoSugarのフック検知!]";
            } else if (className.contains("forbiddenthings")) {
                color = "§4";
                tag = " [★ForbiddenThingsのフック検知!]";
            } else if (className.contains("hyperdamagelib")) {
                color = "§d";
                tag = " [HDL]";
            } else if (className.startsWith("net.minecraft.")) {
                color = "§f";
                tag = " [Vanilla]";
            }
            sb.append(String.format("  %s[%02d] %s#%s (Line:%d)%s\n", color, depth++, className, mName, line, tag));
        }
        sb.append("§d==================================================================");
        HDL.LOGGER.info(sb.toString());
    }
}