package com.maxwell.hyperdamagelib.util;

import com.maxwell.hyperdamagelib.HDL;
import com.test.nosugar.NoSugar;
import java.util.ArrayDeque;
import java.util.Queue;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(
        modid = HDL.MODID,
        bus = Bus.FORGE
)
public class TaskScheduler {
    private static final Queue<Task> queue = new ArrayDeque();
    private static final Object lock = new Object();

    public static void schedule(Runnable action, int delay) {
        if (action == null) {
            throw new IllegalArgumentException("Task action cannot be null");
        } else if (delay < 0) {
            throw new IllegalArgumentException("Delay cannot be negative: " + delay);
        } else {
            synchronized(lock) {
                queue.add(new Task(action, delay));
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase == Phase.END) {
            synchronized(lock) {
                queue.removeIf((task) -> {
                    if (task == null) {
                        return true;
                    } else if (task.action == null) {
                        return true;
                    } else {
                        --task.delay;
                        if (task.delay <= 0) {
                            try {
                                task.action.run();
                            } catch (Exception ex) {
                                NoSugar.LOGGER.error("[NoSugar] Task execution failed: " + ex.getMessage(), ex);
                            }

                            return true;
                        } else {
                            return false;
                        }
                    }
                });
            }
        }
    }

    private static class Task {
        final Runnable action;
        int delay;

        Task(Runnable action, int delay) {
            this.action = action;
            this.delay = delay;
        }

        public String toString() {
            int var10000 = this.delay;
            return "Task{delay=" + var10000 + ", action=" + this.action.getClass().getSimpleName() + "}";
        }
    }
}
