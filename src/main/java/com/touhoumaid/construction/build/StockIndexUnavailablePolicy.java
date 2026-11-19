package com.touhoumaid.construction.build;

/** When auto-craft is on, do not permanently blacklist missing block ids after a failed craft. */
public final class StockIndexUnavailablePolicy {
    private StockIndexUnavailablePolicy() {
    }

    public static boolean shouldMarkUnavailable(boolean autoCraftEnabled) {
        return !autoCraftEnabled;
    }
}
