package com.touhoumaid.construction.build;

/** Whether ending a construction session should switch the maid's TLM task back to idle. */
public final class ConstructionEndTaskResetPolicy {
    private ConstructionEndTaskResetPolicy() {
    }

    /**
     * @param currentTaskUid      maid's current task id string, or null
     * @param constructionTaskUid construction task id string, or null
     */
    public static boolean shouldSetIdle(String currentTaskUid, String constructionTaskUid) {
        if (currentTaskUid == null || constructionTaskUid == null) {
            return false;
        }
        return currentTaskUid.equals(constructionTaskUid);
    }
}
