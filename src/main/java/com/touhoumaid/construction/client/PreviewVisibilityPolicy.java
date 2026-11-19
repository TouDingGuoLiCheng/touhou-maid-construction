package com.touhoumaid.construction.client;

/**
 * Outline toggle rules: hub restore/sync must not reopen a manually closed preview.
 */
public final class PreviewVisibilityPolicy {
    private PreviewVisibilityPolicy() {
    }

    /** LOCK_ANCHORED / ENTER_BUILDING / ensureAnchored keep the saved preference. */
    public static boolean afterRestoreOrSync(boolean userPreference) {
        return userPreference;
    }

    /** Entering site pick needs the ghost visible. */
    public static boolean afterBeginSitePick() {
        return true;
    }

    /** Arming missing-block highlight needs the ghost visible. */
    public static boolean afterArmMissingHighlight() {
        return true;
    }

    public static boolean afterToggle(boolean previous) {
        return !previous;
    }
}
