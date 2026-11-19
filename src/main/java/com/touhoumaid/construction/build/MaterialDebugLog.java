package com.touhoumaid.construction.build;

import com.mojang.logging.LogUtils;
import com.touhoumaid.construction.block.entity.ConstructionTerminalBE;
import com.touhoumaid.construction.item.ConstructionTerminalItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Server-side diagnostics for false material-shortage reports.
 * Grep log for {@code [TMC-Material]}; set {@link #ENABLED} to false to silence.
 */
public final class MaterialDebugLog {
    public static boolean ENABLED = false;

    private static final Logger LOGGER = LogUtils.getLogger();

    private MaterialDebugLog() {
    }

    public static void enterMaterialWait(
            UUID maidId,
            String maidName,
            BuildJob job,
            String blockId,
            String trigger,
            BlockPos taskPos) {
        if (!ENABLED) {
            return;
        }
        MaterialSource materials = job.materials();
        LOGGER.info(
                "[TMC-Material] ENTER_WAIT trigger={} maid={} ({}) project={} blockId={} taskPos={} "
                        + "activeLayer={} pending={} deferred={} unreachable={} inProgress={} "
                        + "hasPlaceableOnLayer={} source={} matchIds={} countForBlock={} vault={}",
                trigger,
                maidName,
                maidId,
                job.key().projectId(),
                blockId,
                taskPos == null ? "-" : taskPos.toShortString(),
                job.activeLayer(),
                job.debugPendingCount(),
                job.deferredCount(),
                job.debugUnreachableCount(),
                job.debugInProgressCount(),
                job.hasPlaceableTaskOnActiveLayer(),
                describeSource(materials),
                formatMatchIds(blockId),
                MaterialVault.countForBlock(containerOf(materials), blockId),
                vaultSnapshot(materials));
    }

    public static void materialCheckFailed(
            String trigger,
            UUID maidId,
            String maidName,
            BuildJob job,
            String blockId,
            BlockPos taskPos,
            String detail) {
        if (!ENABLED) {
            return;
        }
        MaterialSource materials = job.materials();
        LOGGER.info(
                "[TMC-Material] CHECK_FAIL trigger={} maid={} ({}) blockId={} taskPos={} detail={} "
                        + "hasPlaceableOnLayer={} source={} matchIds={} countForBlock={} vault={}",
                trigger,
                maidName,
                maidId,
                blockId,
                taskPos == null ? "-" : taskPos.toShortString(),
                detail,
                job.hasPlaceableTaskOnActiveLayer(),
                describeSource(materials),
                formatMatchIds(blockId),
                MaterialVault.countForBlock(containerOf(materials), blockId),
                vaultSnapshot(materials));
    }

    public static void tryPlaceFailed(
            BuildJob job,
            String blockId,
            BlockPos taskPos,
            String stage) {
        if (!ENABLED) {
            return;
        }
        MaterialSource materials = job.materials();
        LOGGER.info(
                "[TMC-Material] TRY_PLACE_FAIL stage={} blockId={} taskPos={} project={} "
                        + "source={} matchIds={} countForBlock={} vault={}",
                stage,
                blockId,
                taskPos.toShortString(),
                job.key().projectId(),
                describeSource(materials),
                formatMatchIds(blockId),
                MaterialVault.countForBlock(containerOf(materials), blockId),
                vaultSnapshot(materials));
    }

    public static void materialWaitPoll(
            UUID maidId,
            String maidName,
            BuildJob job,
            String blockId,
            boolean hasWaitBlock,
            boolean hasPlaceableOnLayer) {
        if (!ENABLED) {
            return;
        }
        MaterialSource materials = job.materials();
        LOGGER.info(
                "[TMC-Material] WAIT_POLL maid={} ({}) waitingFor={} hasWaitBlock={} hasPlaceableOnLayer={} "
                        + "activeLayer={} source={} matchIds={} countForBlock={} vault={}",
                maidName,
                maidId,
                blockId,
                hasWaitBlock,
                hasPlaceableOnLayer,
                job.activeLayer(),
                describeSource(materials),
                formatMatchIds(blockId),
                MaterialVault.countForBlock(containerOf(materials), blockId),
                vaultSnapshot(materials));
    }

    public static void materialWaitResume(
            UUID maidId,
            String maidName,
            BuildJob job,
            String blockId,
            boolean resumedBecauseStocked,
            boolean resumedBecauseOtherTask) {
        if (!ENABLED) {
            return;
        }
        LOGGER.info(
                "[TMC-Material] WAIT_RESUME maid={} ({}) waitedFor={} stocked={} otherTask={} project={}",
                maidName,
                maidId,
                blockId,
                resumedBecauseStocked,
                resumedBecauseOtherTask,
                job.key().projectId());
    }

    public static void pickNextStuck(
            UUID maidId,
            String maidName,
            BuildJob job,
            String reason) {
        if (!ENABLED) {
            return;
        }
        MaterialSource materials = job.materials();
        LOGGER.info(
                "[TMC-Material] PICK_STUCK reason={} maid={} ({}) project={} activeLayer={} "
                        + "pending={} deferred={} unreachable={} inProgress={} hasPlaceableOnLayer={} source={} vault={}",
                reason,
                maidName,
                maidId,
                job.key().projectId(),
                job.activeLayer(),
                job.debugPendingCount(),
                job.deferredCount(),
                job.debugUnreachableCount(),
                job.debugInProgressCount(),
                job.hasPlaceableTaskOnActiveLayer(),
                describeSource(materials),
                vaultSnapshot(materials));
    }

    private static String formatMatchIds(String blockId) {
        List<String> ids = MaterialVault.matchItemIds(blockId);
        return ids.isEmpty() ? "[]" : ids.stream().collect(Collectors.joining(", ", "[", "]"));
    }

    private static String describeSource(MaterialSource source) {
        MaterialSource terminal = StockIndexedMaterialSource.unwrapTerminal(source);
        if (terminal instanceof BlockTerminalMaterialSource block) {
            Container container = block.container();
            if (container instanceof ConstructionTerminalBE be) {
                return "block@" + be.getBlockPos().toShortString();
            }
            return "block-terminal";
        }
        if (terminal instanceof ItemTerminalMaterialSource item) {
            return item.debugDescribe();
        }
        return source.getClass().getSimpleName();
    }

    private static Container containerOf(MaterialSource source) {
        return MaterialVaultHelpers.asView(source);
    }

    private static String vaultSnapshot(MaterialSource source) {
        Container container = containerOf(source);
        if (container.getContainerSize() == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        int shown = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (shown > 0) {
                sb.append(", ");
            }
            sb.append(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                    .append('×')
                    .append(stack.getCount());
            shown++;
            if (shown >= 32) {
                sb.append(", …");
                break;
            }
        }
        sb.append(']');
        return sb.toString();
    }
}
