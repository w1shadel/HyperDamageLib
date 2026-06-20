package com.maxwell.hyperdamagelib.agent;

import java.lang.instrument.Instrumentation;
import java.util.logging.Logger;

public class DecayAgent {
    private static final Logger LOGGER = Logger.getLogger("DecayAgent");
    public static volatile Instrumentation INSTRUMENTATION;

    static {
        LOGGER.info("Decay Agent Class Loaded");
    }

    public static void premain(String args, Instrumentation inst) {
        INSTRUMENTATION = inst;
        LOGGER.info("Decay Agent premain initialized");
    }

    public static void agentmain(String args, Instrumentation inst) {
        INSTRUMENTATION = inst;
        LOGGER.info("Decay Agent agentmain initialized");
    }
}