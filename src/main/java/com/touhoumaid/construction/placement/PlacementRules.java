package com.touhoumaid.construction.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Placement math + conflict rules for M4.
 * Default: schematic local (0,0,0) sits at {@code clicked.above()} (build on top of surface).
 * Cover terrain: origin is the clicked block so layer y=0 can replace grass/dirt/sand,
 * flowers/plants, unstripped logs, and leaves.
 *
 * <p>1.20.1 flowers often return false from {@link BlockState#canBeReplaced()} (tall flowers
 * especially), so plant coverage uses tags + {@link BushBlock} rather than that flag alone.
 */
public final class PlacementRules {
    private PlacementRules() {
    }

    /** World origin (min corner) so layer y=0 rests on top of the clicked block. */
    public static BlockPos originOnTopOf(BlockPos clicked) {
        return clicked.above();
    }

    /**
     * @param coverTerrain when true, build replaces the clicked cell (and upward);
     *                     when false, build sits on top of the clicked cell.
     */
    public static BlockPos originForClick(BlockPos clicked, boolean coverTerrain) {
        return coverTerrain ? clicked.immutable() : clicked.above();
    }

    public static BlockPos worldPos(BlockPos origin, int localX, int localY, int localZ) {
        return origin.offset(localX, localY, localZ);
    }

    /**
     * Registry path (no namespace) for {@link BlockTags#LOGS}: keep bark logs / wood,
     * skip stripped variants named {@code stripped_*}.
     */
    public static boolean isUnstrippedLogPath(String path) {
        return path != null && !path.isEmpty() && !path.contains("stripped");
    }

    public static boolean isTerrainCoverable(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        if (state.canBeReplaced()
                || state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SAPLINGS)
                || state.is(BlockTags.REPLACEABLE_BY_TREES)
                || state.getBlock() instanceof BushBlock) {
            return true;
        }
        if (state.is(BlockTags.LEAVES)) {
            return true;
        }
        if (state.is(BlockTags.LOGS)) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
            if (id != null && isUnstrippedLogPath(id.getPath())) {
                return true;
            }
        }
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.FARMLAND)
                || state.is(Blocks.SAND)
                || state.is(Blocks.RED_SAND)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.MUD)
                || state.is(Blocks.MUDDY_MANGROVE_ROOTS)
                || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.STONE)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.COBBLED_DEEPSLATE)
                || state.is(Blocks.TUFF)
                || state.is(Blocks.ANDESITE)
                || state.is(Blocks.DIORITE)
                || state.is(Blocks.GRANITE)
                || state.is(Blocks.SMOOTH_BASALT)
                || state.is(Blocks.BASALT)
                || state.is(Blocks.CALCITE)
                || state.is(Blocks.NETHERRACK)
                || state.is(Blocks.END_STONE)
                || state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.STONE_ORE_REPLACEABLES);
    }

    /** Bedrock and similar: {@code Block#defaultDestroyTime()} is negative. */
    public static boolean isUnbreakableDestroyTime(float destroyTime) {
        return destroyTime < 0.0f;
    }

    public static boolean isUnbreakable(BlockState state) {
        return state != null && isUnbreakableDestroyTime(state.getBlock().defaultDestroyTime());
    }

    /**
     * Minecraft-free conflict decision used by unit tests and {@link #isConflict}.
     *
     * @param existingEmpty      air or {@code canBeReplaced}
     * @param coverTerrain       cover-terrain project flag
     * @param terrainCoverable   {@link #isTerrainCoverable}
     * @param ignoreOverlaps     ignore-overlaps project flag
     * @param unbreakable        bedrock / destroyTime &lt; 0
     */
    public static boolean isHardConflict(
            boolean existingEmpty,
            boolean coverTerrain,
            boolean terrainCoverable,
            boolean ignoreOverlaps,
            boolean unbreakable) {
        if (existingEmpty) {
            return false;
        }
        if (unbreakable) {
            return true;
        }
        if (ignoreOverlaps) {
            return false;
        }
        return !(coverTerrain && terrainCoverable);
    }

    /**
     * @param existing    block currently in the world
     * @param planned     non-air block from the schematic
     * @param coverTerrain whether grass/dirt/sand etc. may be overwritten
     * @return true if this cell is a hard conflict
     */
    public static boolean isConflict(BlockState existing, BlockState planned, boolean coverTerrain) {
        return isConflict(existing, planned, coverTerrain, false);
    }

    /**
     * @param ignoreOverlaps when true, any destroyable solid may be overwritten (bedrock still conflicts)
     */
    public static boolean isConflict(
            BlockState existing, BlockState planned, boolean coverTerrain, boolean ignoreOverlaps) {
        if (planned.isAir()) {
            return false;
        }
        boolean empty = existing.isAir() || existing.canBeReplaced();
        return isHardConflict(
                empty,
                coverTerrain,
                isTerrainCoverable(existing),
                ignoreOverlaps,
                isUnbreakable(existing));
    }

    public static Vec3i absSize(Vec3i signed) {
        return new Vec3i(Math.abs(signed.getX()), Math.abs(signed.getY()), Math.abs(signed.getZ()));
    }

    /** Scan a level region; returns conflict count for planned non-air cells. */
    public static int countConflicts(
            BlockGetter level,
            BlockPos origin,
            int sizeX,
            int sizeY,
            int sizeZ,
            java.util.function.Function<BlockPos, BlockState> plannedAt,
            boolean coverTerrain) {
        return countConflicts(level, origin, sizeX, sizeY, sizeZ, plannedAt, coverTerrain, false);
    }

    public static int countConflicts(
            BlockGetter level,
            BlockPos origin,
            int sizeX,
            int sizeY,
            int sizeZ,
            java.util.function.Function<BlockPos, BlockState> plannedAt,
            boolean coverTerrain,
            boolean ignoreOverlaps) {
        int conflicts = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++) {
                    BlockPos world = worldPos(origin, x, y, z);
                    BlockState planned = plannedAt.apply(new BlockPos(x, y, z));
                    if (planned.isAir()) {
                        continue;
                    }
                    if (isConflict(level.getBlockState(world), planned, coverTerrain, ignoreOverlaps)) {
                        conflicts++;
                    }
                }
            }
        }
        return conflicts;
    }
}
