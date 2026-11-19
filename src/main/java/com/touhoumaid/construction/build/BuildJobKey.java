package com.touhoumaid.construction.build;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;

import java.util.UUID;

/** Identifies one active build job bound to a terminal + project. */
public record BuildJobKey(
        String projectId,
        BlockPos terminalPos,
        boolean itemMode,
        InteractionHand hand,
        UUID ownerId) {
}
