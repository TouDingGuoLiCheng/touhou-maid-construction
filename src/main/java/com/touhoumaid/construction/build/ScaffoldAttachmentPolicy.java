package com.touhoumaid.construction.build;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Vanilla scaffolding must touch scaffold below or a cardinal neighbor scaffold / solid floor. */
public final class ScaffoldAttachmentPolicy {
    private ScaffoldAttachmentPolicy() {
    }

    /**
     * Same rules as {@link ScaffoldingBlock#getDistance}, but any walkable scaffold (vanilla or
     * maid staging) counts as support — otherwise towers of construction scaffolds always read as 7.
     */
    public static int walkableScaffoldDistance(BlockGetter level, BlockPos cell) {
        if (level == null || cell == null) {
            return 7;
        }
        BlockPos below = cell.below();
        BlockState belowState = level.getBlockState(below);
        ScaffoldDistancePolicy.Below support;
        if (ConstructionScaffoldBlocks.isWalkableScaffold(belowState)) {
            support = ScaffoldDistancePolicy.Below.scaffold(scaffoldDistanceValue(belowState));
        } else if (belowState.isFaceSturdy(level, below, Direction.UP)) {
            support = ScaffoldDistancePolicy.Below.STURDY;
        } else {
            support = ScaffoldDistancePolicy.Below.NONE;
        }
        int[] horizontal = new int[4];
        int i = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockState neighbor = level.getBlockState(cell.relative(dir));
            horizontal[i++] = ConstructionScaffoldBlocks.isWalkableScaffold(neighbor)
                    ? scaffoldDistanceValue(neighbor)
                    : ScaffoldDistancePolicy.NO_SCAFFOLD;
        }
        return ScaffoldDistancePolicy.compute(support, horizontal);
    }

    private static int scaffoldDistanceValue(BlockState state) {
        if (state != null && state.hasProperty(ScaffoldingBlock.DISTANCE)) {
            return state.getValue(ScaffoldingBlock.DISTANCE);
        }
        return 0;
    }

    public static boolean canPlaceAt(ServerLevel level, BlockPos cell) {
        return canPlaceAt(level, cell, null);
    }

    public static boolean canPlaceAt(ServerLevel level, BlockPos cell, Iterable<BlockPos> alsoConnected) {
        return canPlaceAt(level, cell, alsoConnected, null);
    }

    /**
     * First scaffold directly above maid feet: she occupies the cell below, so vanilla
     * {@link #hasSupportBelow} sees air. Anchor to {@code maidFeet.below()} instead.
     */
    public static boolean canPlaceTowerSeedAboveMaid(ServerLevel level, BlockPos cell, BlockPos maidFeet) {
        if (level == null || cell == null || maidFeet == null) {
            return false;
        }
        if (!isTowerSeedCell(cell, maidFeet)) {
            return false;
        }
        BlockPos ground = maidFeet.below();
        BlockState groundState = level.getBlockState(ground);
        return groundState.is(Blocks.SCAFFOLDING)
                || ConstructionScaffoldBlocks.isMaidPlacedScaffold(groundState)
                || groundState.isFaceSturdy(level, ground, Direction.UP);
    }

    public static boolean canPlaceAt(
            ServerLevel level, BlockPos cell, Iterable<BlockPos> alsoConnected, BlockPos maidFeet) {
        if (level != null) {
            if (hasSupportBelow(level, cell)) {
                return true;
            }
            if (hasCardinalScaffoldNeighbor(level, cell)) {
                return true;
            }
        }
        if (alsoConnected != null) {
            BlockPos below = cell.below();
            for (BlockPos other : alsoConnected) {
                if (other == null) {
                    continue;
                }
                if (other.equals(below)) {
                    return true;
                }
                if (isCardinalNeighbor(cell, other)) {
                    return true;
                }
            }
        }
        return canPlaceTowerSeedAboveMaid(level, cell, maidFeet);
    }

    static boolean isTowerSeedCell(BlockPos cell, BlockPos maidFeet) {
        return isTowerSeedCell(
                cell.getX(), cell.getY(), cell.getZ(),
                maidFeet.getX(), maidFeet.getY(), maidFeet.getZ());
    }

    static boolean isTowerSeedCell(int cx, int cy, int cz, int fx, int fy, int fz) {
        return cx == fx && cz == fz && cy == fy + 1;
    }

    /** After neighbours exist, distance 7 means the block would break on the next tick. */
    public static boolean wouldStayPlaced(ServerLevel level, BlockPos cell) {
        return walkableScaffoldDistance(level, cell) < 7;
    }

    /**
     * Vanilla {@link ScaffoldingBlock#getDistance} ignores maid staging scaffolds and sees air under
     * the maid's feet cell; tower seeds anchored to {@code maidFeet.below()} use distance 1.
     */
    public static int placementDistance(
            ServerLevel level, BlockPos cell, Iterable<BlockPos> alsoConnected, BlockPos maidFeet) {
        if (level != null
                && isTowerSeedCell(cell, maidFeet)
                && canPlaceTowerSeedAboveMaid(level, cell, maidFeet)) {
            return 1;
        }
        return walkableScaffoldDistance(level, cell);
    }

    public static boolean wouldStayPlacedAfterPlacement(
            ServerLevel level, BlockPos cell, Iterable<BlockPos> alsoConnected, BlockPos maidFeet) {
        if (level != null
                && isTowerSeedCell(cell, maidFeet)
                && canPlaceTowerSeedAboveMaid(level, cell, maidFeet)) {
            return true;
        }
        return wouldStayPlaced(level, cell);
    }

    private static boolean hasSupportBelow(ServerLevel level, BlockPos cell) {
        BlockPos below = cell.below();
        BlockState belowState = level.getBlockState(below);
        return ConstructionScaffoldBlocks.isWalkableScaffold(belowState)
                || belowState.isFaceSturdy(level, below, Direction.UP);
    }

    private static boolean hasCardinalScaffoldNeighbor(ServerLevel level, BlockPos cell) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (ConstructionScaffoldBlocks.isWalkableScaffold(level.getBlockState(cell.relative(dir)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCardinalNeighbor(BlockPos a, BlockPos b) {
        return isCardinalAdjacent(a.getX(), a.getY(), a.getZ(), b.getX(), b.getY(), b.getZ());
    }

    /** Package-visible for unit tests without bootstrapping Minecraft. */
    static boolean isCardinalAdjacent(int ax, int ay, int az, int bx, int by, int bz) {
        return ay == by && Math.abs(ax - bx) + Math.abs(az - bz) == 1;
    }
}
