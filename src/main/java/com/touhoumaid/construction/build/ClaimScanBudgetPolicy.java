package com.touhoumaid.construction.build;

import java.util.ArrayList;
import java.util.List;

/**
 * Caps expensive claim-scan work ({@code canSurvive}, stand occupancy).
 * Material checks are budgeted separately by {@link ClaimRadarPolicy} (outward cursor).
 */
public final class ClaimScanBudgetPolicy {
    /** Max survival checks per {@code claimNearestOnLayer} call. */
    public static final int MAX_SURVIVE_CHECKS_PER_CLAIM = 48;
    /**
     * After distance sort, only this many stocked cells are considered for stand-spread
     * and survival (avoids hasFreeStand on thousands of cells).
     */
    public static final int MAX_STOCKED_FOR_CLAIM_ORDER = 96;

    private ClaimScanBudgetPolicy() {
    }

    public static boolean surviveBudgetExhausted(int surviveChecked, int budget) {
        return surviveChecked >= budget;
    }

    public static <T> List<T> capForClaimOrder(List<T> sortedStocked, int cap) {
        if (sortedStocked == null || sortedStocked.isEmpty() || cap <= 0) {
            return List.of();
        }
        if (sortedStocked.size() <= cap) {
            return sortedStocked;
        }
        return new ArrayList<>(sortedStocked.subList(0, cap));
    }
}
