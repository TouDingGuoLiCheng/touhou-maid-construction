package com.touhoumaid.construction.block;

import com.touhoumaid.construction.build.ConstructionScaffoldBlocks;
import com.touhoumaid.construction.build.ScaffoldAttachmentPolicy;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * Temporary scaffolding placed by construction maids. Blueprint scaffolding stays vanilla
 * {@link net.minecraft.world.level.block.Blocks#SCAFFOLDING} and is not reclaimed.
 * <p>
 * Vanilla {@link ScaffoldingBlock#getDistance} only counts {@code Blocks.SCAFFOLDING}; override
 * survive/tick so maid staging towers support themselves.
 */
public final class ConstructionScaffoldBlock extends ScaffoldingBlock {
    public ConstructionScaffoldBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return ScaffoldAttachmentPolicy.walkableScaffoldDistance(level, pos) < 7;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int distance = ScaffoldAttachmentPolicy.walkableScaffoldDistance(level, pos);
        BlockState updated = state
                .setValue(DISTANCE, distance)
                .setValue(WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER)
                .setValue(BOTTOM, isFloatingBottom(level, pos, distance));
        if (distance == 7) {
            if (state.getValue(DISTANCE) == 7) {
                FallingBlockEntity.fall(level, pos, updated);
            } else {
                level.destroyBlock(pos, true);
            }
        } else if (state != updated) {
            level.setBlock(pos, updated, 3);
        }
    }

    private static boolean isFloatingBottom(BlockGetter level, BlockPos pos, int distance) {
        return distance > 0 && !ConstructionScaffoldBlocks.isWalkableScaffold(level.getBlockState(pos.below()));
    }
}
