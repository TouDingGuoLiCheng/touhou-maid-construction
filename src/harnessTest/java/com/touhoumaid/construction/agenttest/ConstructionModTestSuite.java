package com.touhoumaid.construction.agenttest;

import com.mcagent.harness.api.ModTestSuite;
import com.mcagent.harness.api.StepTests;
import com.mcagent.harness.api.TestContext;
import com.mcagent.harness.api.TestFailureException;
import com.mcagent.harness.api.TestRegistration;
import com.touhoumaid.construction.ConstructionMod;
import com.touhoumaid.construction.block.entity.ConstructionTerminalBE;
import com.touhoumaid.construction.project.BuildProject;
import com.touhoumaid.construction.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

public final class ConstructionModTestSuite implements ModTestSuite {
    private static final String FLAT_SCHEMATIC = "agent_flat_stone.litematic";
    private static final String TOWER_SCHEMATIC = "agent_scaffold_tower.litematic";
    private static final String TERMINAL_BLOCK = ModBlocks.CONSTRUCTION_TERMINAL.get().builtInRegistryHolder()
            .key().location().toString();

    @Override
    public String modId() {
        return ConstructionMod.MOD_ID;
    }

    @Override
    public void register(TestRegistration registration) {
        registration.add("construction_load_smoke", StepTests.steps(
                ctx -> ctx.tp(ConstructionAgentTestHelper.ORIGIN_X, ConstructionAgentTestHelper.ORIGIN_Y,
                        ConstructionAgentTestHelper.ORIGIN_Z),
                ctx -> runOnServer(ctx, ConstructionModTestSuite::prepareSmokeSite),
                ctx -> ctx.assertEventually(20, "terminal_block", () -> {
                    BlockPos terminalPos = terminalPos();
                    return TERMINAL_BLOCK.equals(ctx.getBlock(
                            terminalPos.getX(), terminalPos.getY(), terminalPos.getZ()));
                }),
                ctx -> ctx.runCommand("tmc_blueprint dump schematics/" + FLAT_SCHEMATIC),
                ctx -> ctx.pass()
        ));

        registration.add("construction_flat_place", StepTests.steps(
                ctx -> ctx.tp(ConstructionAgentTestHelper.ORIGIN_X, ConstructionAgentTestHelper.ORIGIN_Y,
                        ConstructionAgentTestHelper.ORIGIN_Z),
                ctx -> ctx.runCommand("gamemode creative"),
                ctx -> runOnServer(ctx, (level, player) -> startBuild(level, player, FLAT_SCHEMATIC)),
                ctx -> ctx.waitTicks(20),
                ctx -> ctx.assertEventually(400, "flat_stone_placed", () -> {
                    BlockPos anchor = anchorPos();
                    return "minecraft:stone".equals(ctx.getBlock(anchor.getX(), anchor.getY(), anchor.getZ()));
                }),
                ctx -> ctx.pass()
        ));

        registration.add("construction_scaffold_tower", StepTests.steps(
                ctx -> ctx.tp(ConstructionAgentTestHelper.ORIGIN_X, ConstructionAgentTestHelper.ORIGIN_Y,
                        ConstructionAgentTestHelper.ORIGIN_Z),
                ctx -> ctx.runCommand("gamemode creative"),
                ctx -> runOnServer(ctx, (level, player) -> startBuild(level, player, TOWER_SCHEMATIC)),
                ctx -> ctx.waitTicks(40),
                ctx -> ctx.assertEventually(600, "scaffold_placed", () -> hasScaffoldNearAnchor(ctx)),
                ctx -> ctx.assertEventually(800, "tower_top_stone", () -> {
                    BlockPos top = anchorPos().offset(0, 3, 0);
                    return "minecraft:stone".equals(ctx.getBlock(top.getX(), top.getY(), top.getZ()));
                }),
                ctx -> ctx.pass()
        ));
    }

    private static void prepareSmokeSite(ServerLevel level, ServerPlayer player) throws Exception {
        BlockPos origin = ConstructionAgentTestHelper.origin();
        ConstructionAgentTestHelper.prepareFlatSite(level, origin, 4);
        ConstructionAgentTestHelper.placeTerminal(level, terminalPos());
        ConstructionAgentTestHelper.writeSchematic(FLAT_SCHEMATIC, 1, 1, 1);
    }

    private static void startBuild(ServerLevel level, ServerPlayer player, String schematic) throws Exception {
        BlockPos origin = ConstructionAgentTestHelper.origin();
        ConstructionAgentTestHelper.prepareFlatSite(level, origin, 6);
        BlockPos terminalPos = terminalPos();
        BlockPos maidPos = origin.offset(1, 0, 1);
        BlockPos anchor = anchorPos();

        ConstructionAgentTestHelper.placeTerminal(level, terminalPos);
        Entity maid = ConstructionAgentTestHelper.spawnOwnedMaid(level, player, maidPos);

        BuildProject project = ConstructionAgentTestHelper.installProject(
                level, terminalPos, schematic, anchor, maid.getUUID());
        ConstructionTerminalBE terminal = ConstructionAgentTestHelper.requireTerminal(level, terminalPos);
        ConstructionAgentTestHelper.stockTerminalMaterials(terminal, 64, 64);

        if (!ConstructionAgentTestHelper.startBuild(player, project, terminalPos)) {
            throw new TestFailureException("BuildJobService.start returned false");
        }
    }

    private static void runOnServer(TestContext ctx, ServerWork work) throws Exception {
        ctx.onServer((server, player) -> work.run(
                ((net.minecraft.server.MinecraftServer) server).overworld(),
                (ServerPlayer) player));
    }

    private static BlockPos terminalPos() {
        return ConstructionAgentTestHelper.origin().offset(2, 0, 0);
    }

    private static BlockPos anchorPos() {
        return ConstructionAgentTestHelper.origin().offset(4, 0, 2);
    }

    private static boolean hasScaffoldNearAnchor(TestContext ctx) {
        BlockPos anchor = anchorPos();
        for (int dy = 0; dy <= 3; dy++) {
            BlockPos pos = anchor.offset(0, dy, 0);
            if ("minecraft:scaffolding".equals(ctx.getBlock(pos.getX(), pos.getY(), pos.getZ()))) {
                return true;
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = anchor.offset(dx, 0, dz);
                if ("minecraft:scaffolding".equals(ctx.getBlock(pos.getX(), pos.getY(), pos.getZ()))) {
                    return true;
                }
            }
        }
        return false;
    }

    @FunctionalInterface
    private interface ServerWork {
        void run(ServerLevel level, ServerPlayer player) throws Exception;
    }
}
