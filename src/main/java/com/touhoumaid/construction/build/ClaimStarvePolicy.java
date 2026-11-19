package com.touhoumaid.construction.build;

/** Short claim-scan pause when the active layer has stocked-zero work. */
public final class ClaimStarvePolicy {
    public static final int STARVE_TICKS = 10;

    private ClaimStarvePolicy() {
    }

    public static int starveUntilTick(int nowTick) {
        return nowTick + STARVE_TICKS;
    }

    /**
     * Skip full claim scans while starving, unless the stock index revision changed
     * (deposit / consume / craft-fail mark).
     */
    public static boolean shouldSkip(int nowTick, int starveUntilTick, int indexRevision, int starveRevision) {
        if (indexRevision != starveRevision) {
            return false;
        }
        return nowTick < starveUntilTick;
    }

    /** Enter starve only when some tasks were rejected for material and none passed material. */
    public static boolean shouldEnterStarve(int materialRejected, int materialAccepted) {
        return materialRejected > 0 && materialAccepted <= 0;
    }

    /**
     * Enter starve when every stocked candidate was survival-checked and none passed.
     * Do not starve merely because the per-claim survive budget ran out — more cells remain.
     */
    public static boolean shouldEnterSurviveStarve(int surviveChecked, int survivePassed, int stockedCount) {
        return stockedCount > 0
                && surviveChecked > 0
                && survivePassed <= 0
                && surviveChecked >= stockedCount;
    }
}
