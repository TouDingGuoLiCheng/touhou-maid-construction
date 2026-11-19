package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;

import java.util.List;

/**
 * Scaffold planning / path / teleport diagnostics. Grep {@code [TMC-Scaffold]}; enable with {@code /tmc debug on}
 * or {@code -Dtouhou_maid_construction.debug=true}.
 * Maid label: {@link ConstructionDebugIds#maid(EntityMaid)}.
 */
public final class ScaffoldDebugLog {
    private static final Logger LOGGER = LogUtils.getLogger();

    private ScaffoldDebugLog() {
    }

    private static void info(String format, Object... args) {
        if (ConstructionDebug.enabled()) {
            LOGGER.info(format, args);
        }
    }

    public static void column(
            EntityMaid maid,
            int maidX,
            int maidZ,
            int feetX,
            int feetZ,
            int colX,
            int colZ,
            int existingColumns,
            boolean reused) {
        info(
                "[TMC-Scaffold] COLUMN maid={} maidXz={},{} workFeetXz={},{} col={},{} existing={} reused={}",
                ConstructionDebugIds.maid(maid),
                maidX,
                maidZ,
                feetX,
                feetZ,
                colX,
                colZ,
                existingColumns,
                reused);
    }

    /**
     * @param reason one of {@code maid-outside}, {@code cell-outside}, {@code relaxed-pass}, {@code pass}
     */
    public static void boundsGate(
            EntityMaid maid,
            BlockPos maidAt,
            ScaffoldBoundsPolicy.Box box,
            int margin,
            String reason) {
        info(
                "[TMC-Scaffold] BOUNDS maid={} maidAt={} box={} margin={} reason={}",
                ConstructionDebugIds.maid(maid),
                shortPos(maidAt),
                shortBox(box),
                margin,
                reason);
    }

    /**
     * @param source one of {@code trivialGeometry}, {@code vanilla}, {@code scaffoldGraph},
     *               {@code reuseExistingScaffold}, {@code simPathExisting}
     */
    public static void notNeeded(EntityMaid maid, BlockPos stand, String source) {
        info(
                "[TMC-Scaffold] NOT-NEEDED maid={} stand={} via={}",
                ConstructionDebugIds.maid(maid),
                shortPos(stand),
                source);
    }

    /**
     * Approach simulation: solid work stand + air-as-future-scaffold path.
     * Grep {@code [TMC-Scaffold] SIM}.
     */
    public static void approachSim(
            EntityMaid maid,
            BlockPos maidAt,
            BlockPos target,
            BlockPos workStand,
            String outcome,
            int existingPassable,
            int airPassable,
            int pathLen,
            int toPlace,
            String routeSummary) {
        info(
                "[TMC-Scaffold] SIM maid={} maidAt={} target={} stand={} outcome={} existing={} air={} pathLen={} toPlace={} route={}",
                ConstructionDebugIds.maid(maid),
                shortPos(maidAt),
                shortPos(target),
                shortPos(workStand),
                outcome,
                existingPassable,
                airPassable,
                pathLen,
                toPlace,
                routeSummary == null || routeSummary.isEmpty() ? "-" : routeSummary);
    }

    public static void reclaimOutOfRange(BuildJob job, ScaffoldBoundsPolicy.Box box, int margin, int removed) {
        info(
                "[TMC-Scaffold] RECLAIM-OOR job={} box={} margin={} removed={}",
                job == null ? "-" : job.key(),
                shortBox(box),
                margin,
                removed);
    }

    public static void fillUnderFeet(EntityMaid maid, BlockPos cell) {
        info(
                "[TMC-Scaffold] FILL maid={} cell={}",
                ConstructionDebugIds.maid(maid),
                shortPos(cell));
    }

    public static void plan(
            EntityMaid maid,
            BlockPos maidAt,
            BlockPos target,
            ScaffoldPlanner.PlanStatus status,
            BlockPos workFeet,
            int pathLen,
            int toPlace,
            int ledger) {
        info(
                "[TMC-Scaffold] PLAN maid={} maidAt={} target={} status={} workFeet={} pathLen={} toPlace={} ledger={}",
                ConstructionDebugIds.maid(maid),
                shortPos(maidAt),
                shortPos(target),
                status,
                shortPos(workFeet),
                pathLen,
                toPlace,
                ledger);
    }

    public static void next(
            EntityMaid maid,
            ScaffoldIncrementPolicy.Kind kind,
            ScaffoldLayout.Cell cell,
            ScaffoldLayout.Cell stand,
            BlockPos maidAt) {
        info(
                "[TMC-Scaffold] NEXT maid={} kind={} cell={} stand={} maidAt={}",
                ConstructionDebugIds.maid(maid),
                kind,
                shortCell(cell),
                shortCell(stand),
                shortPos(maidAt));
    }

    public static void path(
            EntityMaid maid,
            BlockPos from,
            BlockPos to,
            int routeLen,
            int passable,
            boolean usedScaffoldNav,
            boolean onScaffold,
            boolean deferLandNav) {
        info(
                "[TMC-Scaffold] PATH maid={} from={} to={} routeLen={} passable={} scaffoldNav={} onScaffold={} deferLand={}",
                ConstructionDebugIds.maid(maid),
                shortPos(from),
                shortPos(to),
                routeLen,
                passable,
                usedScaffoldNav,
                onScaffold,
                deferLandNav);
    }

    public static void tryStart(
            EntityMaid maid,
            BlockPos maidAt,
            float restrictR,
            BlockPos target,
            int failStreak,
            boolean vaultHas,
            int maidBag,
            int ledger) {
        info(
                "[TMC-Scaffold] TRY maid={} maidAt={} restrictR={} target={} failStreak={} vaultHas={} maidBag={} ledger={}",
                ConstructionDebugIds.maid(maid),
                shortPos(maidAt),
                restrictR,
                shortPos(target),
                failStreak,
                vaultHas,
                maidBag,
                ledger);
    }

    public static void result(
            EntityMaid maid,
            ScaffoldService.Attempt attempt,
            BlockPos workFeet,
            int placed,
            BlockPos target) {
        info(
                "[TMC-Scaffold] RESULT maid={} outcome={} workFeet={} placed={} target={}",
                ConstructionDebugIds.maid(maid),
                attempt,
                shortPos(workFeet),
                placed,
                shortPos(target));
    }

    public static void teleport(
            EntityMaid maid,
            String reason,
            BlockPos from,
            BlockPos to,
            double siteDist,
            int sameBlockTicks) {
        info(
                "[TMC-Scaffold] TELEPORT maid={} reason={} from={} to={} siteDist={} sameBlockTicks={}",
                ConstructionDebugIds.maid(maid),
                reason,
                shortPos(from),
                shortPos(to),
                String.format("%.1f", siteDist),
                sameBlockTicks);
    }

    public static String summarizeRoute(List<ScaffoldLayout.Cell> route) {
        if (route == null || route.isEmpty()) {
            return "-";
        }
        ScaffoldLayout.Cell first = route.get(0);
        ScaffoldLayout.Cell last = route.get(route.size() - 1);
        return route.size() + ":" + shortCell(first) + "->" + shortCell(last);
    }

    private static String shortPos(BlockPos pos) {
        return pos == null ? "-" : pos.toShortString();
    }

    private static String shortCell(ScaffoldLayout.Cell cell) {
        return cell == null ? "-" : cell.x() + ", " + cell.y() + ", " + cell.z();
    }

    private static String shortBox(ScaffoldBoundsPolicy.Box box) {
        if (box == null) {
            return "-";
        }
        return box.minX() + "," + box.minY() + "," + box.minZ()
                + ".." + box.maxX() + "," + box.maxY() + "," + box.maxZ();
    }
}
