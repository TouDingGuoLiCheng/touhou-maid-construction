package com.touhoumaid.construction.build;

import com.touhoumaid.construction.blueprint.RawBlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/** One block placement unit derived from a schematic region cell. */
public record BuildTask(
        String regionName,
        BlockPos localPos,
        BlockPos worldPos,
        RawBlockState raw,
        @Nullable CompoundTag tileEntityNbt,
        /** Blueprint-relative layer: lowest non-air world Y in the job = 0. */
        int layerIndex,
        /** 0 = solid-first, 1 = attachables / decorations later on the same layer. */
        int attachPriority) {
}
