package com.touhoumaid.construction.agenttest;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.touhoumaid.construction.block.entity.ConstructionTerminalBE;
import com.touhoumaid.construction.build.BlockTerminalMaterialSource;
import com.touhoumaid.construction.build.BuildJobService;
import com.touhoumaid.construction.project.BuildProject;
import com.touhoumaid.construction.project.SchematicFiles;
import com.touhoumaid.construction.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public final class ConstructionAgentTestHelper {
    public static final int ORIGIN_X = 128;
    public static final int ORIGIN_Y = 64;
    public static final int ORIGIN_Z = 128;

    private ConstructionAgentTestHelper() {
    }

    public static BlockPos origin() {
        return new BlockPos(ORIGIN_X, ORIGIN_Y, ORIGIN_Z);
    }

    public static void prepareFlatSite(ServerLevel level, BlockPos origin, int radius) {
        BlockPos from = origin.offset(-radius, -1, -radius);
        BlockPos to = origin.offset(radius, -1, radius);
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, 0, -radius), origin.offset(radius, 3, radius))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
    }

    public static BlockPos placeTerminal(ServerLevel level, BlockPos pos) {
        BlockState terminal = ModBlocks.CONSTRUCTION_TERMINAL.get().defaultBlockState();
        level.setBlockAndUpdate(pos, terminal);
        return pos;
    }

    public static void writeSchematic(String fileName, int sizeX, int sizeY, int sizeZ) throws IOException {
        SchematicFiles.ensureSchematicsDir();
        Path path = SchematicFiles.resolveSchematic(fileName);
        AgentTestLitematicWriter.writeStoneCube(path, sizeX, sizeY, sizeZ);
    }

    public static BuildProject createConfirmedProject(String schematicFile, BlockPos anchor) throws IOException {
        int[] size = schematicSizeFor(schematicFile);
        writeSchematic(schematicFile, size[0], size[1], size[2]);
        BuildProject project = BuildProject.createNew("AgentTest", schematicFile);
        project.setSiteConfirmed(true);
        project.setAnchor(anchor);
        project.setConflictCount(0);
        return project;
    }

    private static int[] schematicSizeFor(String schematicFile) {
        if ("agent_flat_stone.litematic".equals(schematicFile)) {
            return new int[] {1, 1, 1};
        }
        if ("agent_scaffold_tower.litematic".equals(schematicFile)) {
            return new int[] {1, 4, 1};
        }
        return new int[] {1, 1, 1};
    }

    public static EntityMaid spawnOwnedMaid(ServerLevel level, ServerPlayer owner, BlockPos pos) {
        EntityMaid maid = EntityMaid.TYPE.create(level);
        if (maid == null) {
            throw new IllegalStateException("Failed to create maid entity");
        }
        maid.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        maid.tame(owner);
        maid.setPersistenceRequired();
        level.addFreshEntity(maid);
        return maid;
    }

    public static ConstructionTerminalBE requireTerminal(ServerLevel level, BlockPos terminalPos) {
        BlockEntity be = level.getBlockEntity(terminalPos);
        if (!(be instanceof ConstructionTerminalBE terminal)) {
            throw new IllegalStateException("Missing construction terminal at " + terminalPos);
        }
        return terminal;
    }

    public static BuildProject installProject(
            ServerLevel level,
            BlockPos terminalPos,
            String schematicFile,
            BlockPos anchor,
            UUID maidId) throws IOException {
        ConstructionTerminalBE terminal = requireTerminal(level, terminalPos);
        BuildProject project = createConfirmedProject(schematicFile, anchor);
        project.setAssignedMaids(List.of(maidId));
        terminal.addProject(project);
        terminal.setChanged();
        return project;
    }

    public static void stockTerminalMaterials(ConstructionTerminalBE terminal, int stoneCount, int scaffoldCount) {
        terminal.setItem(0, new ItemStack(Items.STONE, stoneCount));
        terminal.setItem(1, new ItemStack(Items.SCAFFOLDING, scaffoldCount));
        terminal.setChanged();
    }

    public static boolean startBuild(ServerPlayer player, BuildProject project, BlockPos terminalPos) {
        ConstructionTerminalBE terminal = requireTerminal(player.serverLevel(), terminalPos);
        return BuildJobService.start(
                player,
                project,
                terminalPos,
                false,
                InteractionHand.MAIN_HAND,
                new BlockTerminalMaterialSource(terminal));
    }
}
