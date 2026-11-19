package com.touhoumaid.construction.build;

/**
 * Runtime diagnostics gate.
 * Enable with JVM flag {@code -Dtouhou_maid_construction.debug=true},
 * or in-game {@code /tmc debug on|off}.
 */
public final class ConstructionDebug {
    private static volatile boolean enabled =
            Boolean.parseBoolean(System.getProperty("touhou_maid_construction.debug", "false"));

    private ConstructionDebug() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }
}
