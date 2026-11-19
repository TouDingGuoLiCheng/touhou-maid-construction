package com.touhoumaid.construction.build;

/**
 * Claim-assign bookkeeping: attempt counters reset when the held cell actually changes.
 *
 * <p>{@code previousHeld} must be the cell held <em>before</em> assigning {@code newlyClaimed}
 * (do not set {@code currentTask} to the new claim first, or same-cell is always true).
 */
public final class BuildClaimAssignPolicy {
    private BuildClaimAssignPolicy() {
    }

    public static boolean shouldResetAttemptCounters(String previousHeld, String newlyClaimed) {
        if (newlyClaimed == null) {
            return true;
        }
        return previousHeld == null || !previousHeld.equals(newlyClaimed);
    }
}
