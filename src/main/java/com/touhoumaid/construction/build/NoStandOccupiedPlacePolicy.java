package com.touhoumaid.construction.build;

/**
 * When a claimed cell has no reachable work stand but is occupied by a wrong destroyable block,
 * clear that single cell and vault-place the schematic — independent of cover/ignore-overlap toggles.
 */
public final class NoStandOccupiedPlacePolicy {
    private NoStandOccupiedPlacePolicy() {
    }

    /**
     * @param hasWorkStand         maid already has a stand for this claim
     * @param targetAir            world cell is air / replaceable empty
     * @param targetMatchesPlanned world already matches the schematic task
     * @param destroyable          not bedrock / unbreakable
     */
    public static boolean shouldClearOccupiedTargetThenPlace(
            boolean hasWorkStand,
            boolean targetAir,
            boolean targetMatchesPlanned,
            boolean destroyable) {
        if (hasWorkStand || targetAir || targetMatchesPlanned) {
            return false;
        }
        return destroyable;
    }
}
