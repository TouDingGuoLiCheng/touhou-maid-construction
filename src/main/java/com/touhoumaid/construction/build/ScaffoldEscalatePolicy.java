package com.touhoumaid.construction.build;

/**
 * After a scaffold attempt has already escalated to direct-place/defer, do not re-enter scaffold
 * planning — that recursion caused {@link StackOverflowError} via
 * {@code handleUnreachableStand → tryScaffold → PLAN_FAILED → tryDirectPlaceOrDefer → …}.
 */
public final class ScaffoldEscalatePolicy {
    private ScaffoldEscalatePolicy() {
    }

    /**
     * @param alreadyInScaffoldEscalate true when called from inside scaffold result handling
     *                                  (PLAN_FAILED / PLACE_BLOCKED / NOT_NEEDED stuck escalate)
     */
    public static boolean allowScaffoldReplan(boolean alreadyInScaffoldEscalate) {
        return !alreadyInScaffoldEscalate;
    }
}
