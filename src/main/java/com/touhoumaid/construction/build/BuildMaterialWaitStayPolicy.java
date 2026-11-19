package com.touhoumaid.construction.build;

/** While waiting for materials, stay put — no wander, follow, or site-rescue yank. */
public final class BuildMaterialWaitStayPolicy {
    private BuildMaterialWaitStayPolicy() {
    }

    public static boolean shouldFreezeInPlace(boolean waitingMaterial) {
        return waitingMaterial;
    }

    public static boolean shouldRunSiteRescue(boolean waitingMaterial) {
        return !waitingMaterial;
    }

    public static boolean shouldClearBody(boolean waitingMaterial) {
        return !waitingMaterial;
    }

    /**
     * Do not shrink {@code restrictTo} during wait. A 1-block pin made the maid path toward the
     * restrict center every tick and looked like stomping in place.
     */
    public static boolean shouldTightenRestrict(boolean waitingMaterial) {
        return false;
    }

    /** Construction-active maids must never {@code teleportToOwner} when the owner walks away. */
    public static boolean shouldBlockOwnerTeleport(boolean constructionActive) {
        return constructionActive;
    }

    public static boolean shouldResetNavigation(boolean waitingMaterial) {
        return waitingMaterial;
    }

    public static boolean shouldZeroVerticalMotion(boolean onGround) {
        return onGround;
    }
}
