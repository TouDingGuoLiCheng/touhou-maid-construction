package com.touhoumaid.construction.build;

/** When to use scaffold stepping vs vanilla land pathfinding, and when to reissue a land path. */
public final class ConstructionWalkPolicy {
    private ConstructionWalkPolicy() {
    }

    public static boolean useScaffoldRoute(int routeLength) {
        return routeLength >= 2;
    }

    /**
     * When scaffold routing reaches the current cell (route length == 1), keep one-tick scaffold
     * hold if the maid is still inside/above scaffolding. This avoids gravity flicker at column
     * tops which can cause sudden drops.
     */
    public static boolean shouldHoldAtScaffoldRouteEnd(int routeLength, boolean onScaffold) {
        return routeLength == 1 && onScaffold;
    }

    /**
     * Never pin the maid on her current scaffold cell just because land BFS failed.
     * That blocks walking onto adjacent dirt and looks like a stand-still.
     */
    public static boolean holdInPlaceOnScaffold() {
        return false;
    }

    /** Snap onto column top only after upward scaffold PLACE, not horizontal spans at the same Y. */
    public static boolean shouldSnapUpAfterVerticalPlace(
            int fx, int fy, int fz, int cx, int cy, int cz) {
        if (cy > fy) {
            return true;
        }
        return cx == fx && cz == fz && cy == fy;
    }

    /**
     * Defer land BFS only while stranded on the <em>work</em> column with no scaffold route
     * (climb/descend). If the stand is on another column, allow land pathfinding so she can leave
     * the incomplete tower instead of sitting forever with {@code deferLand=true}.
     */
    public static boolean shouldDeferLandNavWhileOnScaffold(
            boolean onScaffold, int scaffoldRouteLength, int dxzToWork) {
        return onScaffold && scaffoldRouteLength == 0 && dxzToWork <= 1;
    }

    /**
     * Vertical snap (up/down one cell) is only safe on the work column. Off-column snaps pin the maid
     * on a half-built tower.
     */
    public static boolean shouldAllowVerticalScaffoldSnap(int dxzToWork) {
        return dxzToWork <= 1;
    }

    /**
     * When land BFS to the work cell is empty but the maid is on flat ground within two blocks,
     * issue a direct walk instead of waiting for stuck teleport.
     */
    public static boolean shouldNudgeFlatGround(
            boolean onScaffold, int scaffoldRouteLength, int manhattanDist, int dy) {
        return !onScaffold && scaffoldRouteLength == 0 && manhattanDist <= 2 && Math.abs(dy) <= 1;
    }

    /**
     * Same-layer step off scaffold onto an adjacent stand (solid or scaffold) when graph routing
     * has no horizontal progress.
     */
    public static boolean shouldNudgeScaffoldWalkway(
            boolean onScaffold, int scaffoldRouteLength, int manhattanDist, int dy, int dxz) {
        if (!onScaffold || scaffoldRouteLength != 0 || manhattanDist > 24) {
            return false;
        }
        return dxz > 0 && Math.abs(dy) <= 1;
    }

    /** Bypass long passable-graph routes: climb one cell on the work column first. */
    public static boolean shouldPreferDirectColumnClimb(
            boolean onScaffold, int dyToWork, int dxzToWork, int feetY, int workY) {
        return onScaffold && dyToWork > 0 && dxzToWork <= 1 && feetY < workY;
    }

    /** Sideways progress at the same layer (scaffold arm or step onto solid footing). */
    public static boolean shouldStepOffScaffoldHorizontally(
            boolean onScaffold, int dxzToWork, int dyToWork) {
        return onScaffold && dxzToWork > 0 && Math.abs(dyToWork) <= 1;
    }

    /**
     * One-cell (or diagonal-two) stand: keep walking with a precise path. Do not teleport —
     * that looks like sliding with no facing.
     */
    public static boolean shouldSnapAdjacentWork(int manhattanToWork, int stuckTicks) {
        return false;
    }

    public static final int NEAR_REPATH_COOLDOWN_TICKS = 15;

    /**
     * Reissue a land path only when the destination changed, navigation never started,
     * or a near miss has waited out the cooldown. {@code nav.isDone()} alone must not
     * repath every tick (that oscillates one block forward/back).
     */
    public static boolean shouldRefreshVanillaPath(
            boolean destChanged,
            boolean navInProgress,
            boolean navDone,
            int manhattanToWork,
            int ticksSincePathIssue) {
        if (destChanged) {
            return true;
        }
        if (navInProgress && !navDone) {
            return false;
        }
        if (navDone) {
            if (manhattanToWork <= 0) {
                return false;
            }
            return ticksSincePathIssue >= NEAR_REPATH_COOLDOWN_TICKS;
        }
        return true;
    }

    public static boolean shouldRefreshVanillaPath(
            boolean destChanged, boolean navInProgress, boolean navDone) {
        return shouldRefreshVanillaPath(destChanged, navInProgress, navDone, 8, 0);
    }

    /** Completion radius for {@code createPath}: exact when already close. */
    public static int vanillaPathRange(int manhattanToWork) {
        return manhattanToWork <= 2 ? 0 : 1;
    }

    /** Repeated "scaffold not needed, just walk" while still stuck → escalate to direct place. */
    public static boolean shouldEscalateNotNeededWalk(int notNeededWhileStuck) {
        return notNeededWhileStuck >= 2;
    }

    /**
     * On scaffolding with an empty route and higher work: climb only when already near the work
     * column (XZ). Otherwise the maid shoots up the first tower she grazes before walking in.
     */
    public static boolean shouldClimbTowardHigherWork(
            boolean onScaffold, int scaffoldRouteLength, int dyToWork, int dxzToWork) {
        return onScaffold && scaffoldRouteLength == 0 && dyToWork > 0 && dxzToWork <= 1;
    }

    /**
     * Only hunt an alternate stand when the maid is already next to the same workFeet.
     * Being anywhere else on scaffolding must NOT count as "near" — that swapped the stand to an
     * air cell above the tower and caused endless up/down bobbing at the top.
     */
    public static boolean shouldSeekAlternateStand(boolean sameWorkFeet, int manhattanToWork) {
        return sameWorkFeet && manhattanToWork <= 2;
    }

    /**
     * While climbing, ignore a scaffold APPROACH stand that is well below the maid's feet.
     * Re-targeting the tower base every place-cooldown tick reverses climb velocity (jump loop).
     */
    public static boolean shouldIgnoreLowerApproachStand(boolean onScaffold, int proposedY, int feetY) {
        return onScaffold && proposedY < feetY - 1;
    }

    /**
     * Mid-climb on the work column: keep the current stand when a replan proposes another column
     * or a lower mid-path cell. Otherwise workFeet thrash cancels climb progress.
     */
    public static boolean shouldRetainClimbWorkFeet(
            boolean onScaffold,
            int feetX,
            int feetY,
            int feetZ,
            int currentWorkX,
            int currentWorkY,
            int currentWorkZ,
            int proposedX,
            int proposedY,
            int proposedZ) {
        if (!onScaffold || currentWorkY <= feetY) {
            return false;
        }
        int curDxz = Math.abs(currentWorkX - feetX) + Math.abs(currentWorkZ - feetZ);
        if (curDxz > 1) {
            return false;
        }
        if (proposedX == currentWorkX && proposedZ == currentWorkZ && proposedY >= currentWorkY) {
            return false;
        }
        return proposedX != currentWorkX || proposedZ != currentWorkZ || proposedY < currentWorkY;
    }

    /** True when the maid is on scaffolding below a near-column work stand. */
    public static boolean isMidClimbSameColumn(boolean onScaffold, int dyToWork, int dxzToWork) {
        return onScaffold && dyToWork > 0 && dxzToWork <= 1;
    }

    /**
     * While climbing, reject replans that swap back to a lower APPROACH stand the maid has
     * already climbed above (prevents -49 / -52 workFeet thrash on adjacent columns).
     */
    public static boolean shouldRetainHigherClimbTarget(
            boolean onScaffold, int feetY, int currentWorkY, int proposedY) {
        return onScaffold && feetY < currentWorkY && proposedY < currentWorkY;
    }

    /** Do not step up the scaffold while still offset sideways from the work column. */
    public static boolean shouldBlockUpwardScaffoldStep(int dxzToWork, int feetY, int stepY) {
        return dxzToWork > 0 && stepY > feetY;
    }

    /** At the physical column top: walk sideways or descend — do not keep pulsing upward. */
    public static boolean isAtColumnTop(int feetY, int columnMaxStandY) {
        return feetY >= columnMaxStandY;
    }

    /** Same-layer horizontal progress along scaffold when work is sideways. */
    public static boolean shouldTakeHorizontalScaffoldStep(boolean onScaffold, int scaffoldRouteLength, int dxzToWork) {
        return onScaffold && dxzToWork > 0;
    }

    /**
     * On scaffolding with no route and the build target still out of interact reach: climb down
     * so land pathfinding can relocate. Do not pin on a tall but useless tower top.
     */
    public static boolean shouldDescendToRelocate(
            boolean onScaffold, boolean canInteractTarget, int scaffoldRouteLength) {
        return onScaffold && !canInteractTarget && scaffoldRouteLength == 0;
    }

    /**
     * Drop the walk stand one cell down the <em>current</em> column before pathing away.
     * Same-column climb (work or wall above, stand nearby) must not be pulled down.
     */
    public static boolean shouldLowerWorkStandToDescend(
            boolean onScaffold,
            boolean canInteractTarget,
            int workY,
            int feetY,
            int dxzToWork,
            int targetY) {
        if (!shouldDescendToRelocate(onScaffold, canInteractTarget, 0)) {
            return false;
        }
        if (dxzToWork <= 1 && (workY > feetY || targetY > feetY)) {
            return false;
        }
        if (dxzToWork > 1) {
            return true;
        }
        return workY >= feetY;
    }

    /**
     * After stepping the walk stand down this pole, ignore a replan that points at another
     * column's lower/equal stand. A higher proposed stand is a climb and must not be kept down.
     */
    public static boolean shouldKeepLocalDescendAgainstOffColumnReplan(
            boolean onScaffold,
            int feetX,
            int feetY,
            int feetZ,
            int currentWorkX,
            int currentWorkY,
            int currentWorkZ,
            int proposedX,
            int proposedY,
            int proposedZ) {
        if (!onScaffold) {
            return false;
        }
        if (proposedY > feetY) {
            return false;
        }
        int curDxz = Math.abs(currentWorkX - feetX) + Math.abs(currentWorkZ - feetZ);
        int propDxz = Math.abs(proposedX - feetX) + Math.abs(proposedZ - feetZ);
        return curDxz <= 1 && currentWorkY <= feetY && propDxz > 1;
    }

    /** Step down one scaffold cell toward lower work on the same column. */
    public static boolean shouldDescendOnScaffold(
            boolean onScaffold, int scaffoldRouteLength, int dyToWork, int dxzToWork, int feetY, int columnMaxStandY) {
        if (!onScaffold || scaffoldRouteLength != 0 || dyToWork >= 0 || dxzToWork > 1) {
            return false;
        }
        return feetY <= columnMaxStandY;
    }

    /** Snap upward when stalled climbing on/near the work column (allows small XZ offset). */
    public static boolean shouldSnapScaffoldProgress(
            boolean onScaffold,
            boolean usingScaffoldNav,
            int dyToWork,
            int dxzToWork,
            int sameBlockTicks,
            int feetY,
            int workY,
            int columnMaxStandY) {
        if (!onScaffold || dyToWork <= 0 || sameBlockTicks < 20) {
            return false;
        }
        if (dxzToWork > 2) {
            return false;
        }
        int capY = Math.min(workY, columnMaxStandY);
        return feetY < capY;
    }

    /**
     * Velocity climb stalled mid-column: snap one block up instead of rebuilding scaffolds and
     * yanking workFeet to a neighbor tower. Never snap at or above the work stand — that stacks
     * noGravity teleports into the sky.
     */
    public static boolean shouldSnapClimbStall(
            boolean onScaffold,
            boolean usingScaffoldNav,
            int dyToWork,
            int dxzToWork,
            int sameBlockTicks,
            int feetY,
            int workY,
            int columnMaxStandY) {
        if (!isMidClimbSameColumn(onScaffold, dyToWork, dxzToWork)
                || !usingScaffoldNav
                || sameBlockTicks < 20) {
            return false;
        }
        int capY = Math.min(workY, columnMaxStandY);
        return feetY < capY;
    }

    public static boolean shouldCorrectFloatingAboveColumn(boolean floatingAboveColumn) {
        return floatingAboveColumn;
    }

    /**
     * Long same-cell stall triggers scaffold rebuild — but not while mid-climb on the work column
     * (that path uses {@link #shouldSnapClimbStall} instead), until a longer timeout.
     */
    public static boolean shouldRebuildScaffoldWhileNav(
            boolean usingScaffoldNav, int sameBlockTicks, boolean midClimbSameColumn) {
        if (!usingScaffoldNav || sameBlockTicks < 40) {
            return false;
        }
        if (midClimbSameColumn && sameBlockTicks < 80) {
            return false;
        }
        return true;
    }

    /**
     * Only hard-stop velocity when actually releasing from no-gravity scaffold motion. Repeated
     * hard stops on normal ground pathing can pin the maid under blocks.
     */
    public static boolean shouldHardStopOnScaffoldRelease(boolean noGravityNow) {
        return noGravityNow;
    }

    /**
     * Safety valve: if the maid still has noGravity but is no longer in scaffold navigation,
     * force releasing hover to avoid floating on flat ground.
     */
    public static boolean shouldReleaseScaffoldHover(boolean noGravityNow, boolean usingScaffoldNav) {
        return noGravityNow && !usingScaffoldNav;
    }

    /**
     * Safety net for rare state leaks: if noGravity remains on while not scaffold-routing and not
     * standing on scaffolding, force disable it.
     */
    public static boolean shouldForceDisableHover(
            boolean noGravityNow, boolean onScaffold, boolean usingScaffoldNav) {
        return noGravityNow && !onScaffold;
    }
}
