package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.task.TaskManager;
import com.touhoumaid.construction.block.entity.ConstructionTerminalBE;
import com.touhoumaid.construction.blueprint.Blueprint;
import com.touhoumaid.construction.blueprint.LitematicReader;
import com.touhoumaid.construction.placement.BlueprintPlacement;
import com.touhoumaid.construction.item.ConstructionTerminalItem;
import com.touhoumaid.construction.build.MaterialVault;
import com.touhoumaid.construction.item.ItemTerminalData;
import com.touhoumaid.construction.network.BuildProgressPacket;
import com.touhoumaid.construction.network.ModNetwork;
import com.touhoumaid.construction.network.SitePreviewSyncPacket;
import com.touhoumaid.construction.project.BuildProject;
import com.touhoumaid.construction.project.SchematicFiles;
import com.touhoumaid.construction.task.TaskConstruction;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.PacketDistributor;

import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Starts, pauses, and completes build jobs tied to construction terminals. */
public final class BuildJobService {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double MAID_SEARCH_RADIUS = 48.0D;
    /** Chunk radius when scanning block terminals during login / delayed resume. */
    private static final int TERMINAL_CHUNK_RADIUS = 8;

    private BuildJobService() {
    }

    public static boolean start(
            ServerPlayer player,
            BuildProject project,
            BlockPos terminalPos,
            boolean itemMode,
            InteractionHand hand,
            MaterialSource materials) {
        if (!project.siteConfirmed() || project.anchor() == null || project.hasConflicts()) {
            return false;
        }
        if (project.assignedMaids().isEmpty()) {
            player.sendSystemMessage(Component.translatable(
                    "message.touhou_maid_construction.no_maid_selected"));
            return false;
        }
        materials = withAutoCraft(materials, project, player.serverLevel());
        BuildJobKey key = new BuildJobKey(
                project.id(), terminalPos, itemMode, hand, player.getUUID());
        Optional<BuildJob> existing = BuildJobRegistry.get(key);
        if (existing.isPresent()) {
            BuildJob job = existing.get();
            BlockPos anchor = project.anchor();
            if (anchor != null && !job.anchor().equals(anchor)) {
                BuildJobRegistry.remove(key);
                ConstructionMaidBinding.clearJob(key);
                existing = Optional.empty();
            }
        }
        if (existing.isPresent()) {
            BuildJob job = existing.get();
            AutoCraftingMaterialSource.setEnabled(job.materials(), project.autoCraftMaterials());
            job.resumeFromPause();
            rescanMissingForRepair(player.serverLevel(), project, key, job);
            assignConstructionMaids(player, project, key);
            ConstructionPlayerBuildIndex.remember(player, key);
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new SitePreviewSyncPacket(
                            SitePreviewSyncPacket.Action.ENTER_BUILDING,
                            project.anchor(),
                            project.schematicPath(),
                            project.coverTerrain(),
                            project.placementYaw(),
                            project.startLayer()));
            return true;
        }
        try {
            Blueprint blueprint = LitematicReader.read(
                    SchematicFiles.resolveSchematic(project.schematicPath()));
            List<BuildTask> tasks = BlueprintTaskBuilder.build(
                    blueprint,
                    project.anchor(),
                    project.placementYaw(),
                    project.startLayer(),
                    project.missingBlockReplacements());
            if (tasks.isEmpty()) {
                player.sendSystemMessage(Component.translatable(
                        "message.touhou_maid_construction.build_empty"));
                return false;
            }
            BuildJob job = new BuildJob(
                    key, blueprint, project.anchor(), materials, tasks, project.ignoredBlockIds(),
                    project.placementYaw(), project.coverTerrain(), project.ignoreOverlaps());
            job.syncAlreadyBuilt(player.serverLevel());
            BuildJobRegistry.register(job);

            project.setBuildSession(key);
            project.setTotalBlocks(job.totalBlocks());
            project.setPlacedBlocks(job.placedCount());
            project.setConsumedMaterials(job.consumedMaterials());
            project.setPlacedByBlockId(job.placedByBlockId());
            project.setPlacementFailures(List.of());
            project.setStatus(BuildProject.Status.BUILDING);

            List<EntityMaid> maids = assignConstructionMaids(player, project, key);
            if (maids.isEmpty()) {
                player.sendSystemMessage(Component.translatable(
                        "message.touhou_maid_construction.no_maid"));
                BuildJobRegistry.remove(key);
                project.setStatus(BuildProject.Status.SITE_READY);
                return false;
            }
            player.sendSystemMessage(Component.translatable(
                    "message.touhou_maid_construction.build_started", maids.size()));
            ConstructionPlayerBuildIndex.remember(player, key);

            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new SitePreviewSyncPacket(
                            SitePreviewSyncPacket.Action.ENTER_BUILDING,
                            project.anchor(),
                            project.schematicPath(),
                            project.coverTerrain(),
                            project.placementYaw(),
                            project.startLayer()));
            return true;
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Build start failed: " + e.getMessage()));
            return false;
        }
    }

    public static void pause(ServerLevel level, BuildProject project, BuildJobKey key) {
        BuildJobRegistry.get(key).ifPresent(job -> job.setPaused(true, "manual"));
        project.setStatus(BuildProject.Status.PAUSED);
        ConstructionMaidFollow.releaseForJob(key, level);
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(key.ownerId());
        if (owner != null) {
            ConstructionPlayerBuildIndex.forget(owner, key);
        }
    }

    public static BuildJobKey jobKeyFor(
            ServerPlayer player,
            BuildProject project,
            BlockPos terminalPos,
            boolean itemMode,
            InteractionHand hand) {
        return new BuildJobKey(
                project.id(),
                itemMode ? BlockPos.ZERO : terminalPos,
                itemMode,
                hand == null ? InteractionHand.MAIN_HAND : hand,
                player.getUUID());
    }

    /**
     * Drop in-memory build state when the player confirms a new site anchor.
     * Old task queues still pointed at the previous origin until this runs.
     */
    public static void onSiteRelocated(ServerPlayer player, BuildProject project, BuildJobKey key) {
        project.buildJobKey().ifPresent(saved -> {
            if (!saved.equals(key)) {
                BuildJobRegistry.remove(saved);
                ConstructionMaidBinding.clearJob(saved);
            }
        });
        if (BuildJobRegistry.get(key).isPresent()) {
            BuildJobRegistry.remove(key);
            ConstructionMaidBinding.clearJob(key);
        }
        project.resetBuildProgress();
        project.clearBuildSession();
        if (project.status() == BuildProject.Status.BUILDING
                || project.status() == BuildProject.Status.PAUSED
                || project.status() == BuildProject.Status.DONE) {
            project.setStatus(BuildProject.Status.SITE_READY);
        }
    }

    /** Pause an active job while the player re-enters site-pick mode. */
    public static void pauseForSitePick(ServerLevel level, BuildProject project, BuildJobKey key) {
        if (project.status() != BuildProject.Status.BUILDING
                && project.status() != BuildProject.Status.PAUSED) {
            return;
        }
        BuildJobRegistry.get(key).ifPresent(job -> job.setPaused(true, "site_pick"));
        project.setStatus(BuildProject.Status.PAUSED);
        ConstructionMaidFollow.releaseForJob(key, level);
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(key.ownerId());
        if (owner != null) {
            ConstructionPlayerBuildIndex.forget(owner, key);
        }
    }

    public static void notifyJobComplete(BuildJob job, ServerLevel level) {
        if (!job.tryMarkCompletionNotified()) {
            return;
        }
        onJobDone(job, level);
    }

    public static void onJobDone(BuildJob job, ServerLevel level) {
        ScaffoldService.reclaimAll(level, job);
        BuildJobKey key = job.key();
        ConstructionMaidFollow.endForJob(key, level);
        BuildJobRegistry.remove(key);
        ConstructionMaidBinding.clearJob(key);
        String projectName = resolveProjectName(level, key).orElse(key.projectId());
        updateProjectByKey(level, key, project -> {
            project.setPlacedBlocks(job.placedCount());
            project.setConsumedMaterials(job.consumedMaterials());
            project.setPlacedByBlockId(job.placedByBlockId());
            project.setPlacementFailures(job.placementFailures());
            project.clearBuildSession();
            project.setStatus(BuildProject.Status.DONE);
        });
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(key.ownerId());
        if (owner != null) {
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> owner),
                    new SitePreviewSyncPacket(
                            SitePreviewSyncPacket.Action.CLEAR,
                            null,
                            "",
                            false));
            owner.sendSystemMessage(Component.translatable(
                    "message.touhou_maid_construction.build_done", projectName));
            ConstructionPlayerBuildIndex.forget(owner, key);
        }
    }

    /**
     * Force-complete at any project status: stop maids, skip remaining placements,
     * reclaim scaffolds if a job exists, mark DONE.
     */
    public static boolean forceFinish(ServerPlayer player, BuildProject project, BuildJobKey key) {
        ServerLevel level = player.serverLevel();
        Optional<BuildJob> jobOpt = BuildJobRegistry.get(key);
        // Always release maids / stay areas, even when the in-memory job is already gone.
        ConstructionMaidFollow.endForJob(key, level);
        ConstructionMaidBinding.clearJob(key);
        if (jobOpt.isEmpty()) {
            reclaimOrphanScaffolds(player, level, project, key);
            markProjectForceFinished(project, null);
            persistForceFinished(player, level, key, project);
            notifyForceFinished(player, project.name());
            ConstructionPlayerBuildIndex.forget(player, key);
            return true;
        }
        BuildJob job = jobOpt.get();
        job.setPaused(false, "");
        String projectName = resolveProjectName(level, key).orElse(project.name());
        ScaffoldService.reclaimAll(level, job);
        BuildJobRegistry.remove(key);
        markProjectForceFinished(project, job);
        persistForceFinished(player, level, key, project);
        notifyForceFinished(player, projectName);
        ConstructionPlayerBuildIndex.forget(player, key);
        return true;
    }

    /**
     * The in-memory ledger is empty once the job is gone (including after rejoin). Still break
     * maid towers standing around the footprint, including ones outside the tight build margin.
     */
    private static void reclaimOrphanScaffolds(
            ServerPlayer player, ServerLevel level, BuildProject project, BuildJobKey key) {
        if (project.anchor() == null) {
            return;
        }
        ScaffoldBoundsPolicy.Box box = readScaffoldBox(project);
        if (box == null) {
            return;
        }
        MaterialSource materials = resolveMaterialsForRestore(player, level, key);
        ScaffoldService.reclaimAround(level, box, materials, project.anchor());
    }

    private static ScaffoldBoundsPolicy.Box readScaffoldBox(BuildProject project) {
        try {
            Blueprint blueprint = LitematicReader.read(SchematicFiles.resolveSchematic(project.schematicPath()));
            BlueprintPlacement.Footprint footprint = BlueprintPlacement.unrotatedFootprint(blueprint);
            BlueprintPlacement.Bounds bounds = BlueprintPlacement.placedBounds(
                    project.anchor(), footprint, project.placementYaw());
            return new ScaffoldBoundsPolicy.Box(
                    bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ());
        } catch (Exception e) {
            LOGGER.warn("[TMC-Scaffold] orphan reclaim skipped, schematic unreadable project={}", project.id(), e);
            return null;
        }
    }

    private static void markProjectForceFinished(BuildProject project, BuildJob job) {
        if (job != null) {
            project.setPlacedBlocks(job.placedCount());
            project.setConsumedMaterials(job.consumedMaterials());
            project.setPlacedByBlockId(job.placedByBlockId());
            project.setPlacementFailures(job.placementFailures());
        }
        project.clearBuildSession();
        project.setStatus(BuildProject.Status.DONE);
    }

    /**
     * Persist DONE onto the terminal/item copy. Also mutates {@code project} first so a later
     * caller {@code saveProjects} cannot overwrite DONE with a stale BUILDING snapshot.
     */
    private static void persistForceFinished(
            ServerPlayer player, ServerLevel level, BuildJobKey key, BuildProject project) {
        updateProjectByKey(level, key, p -> {
            p.setPlacedBlocks(project.placedBlocks());
            p.setConsumedMaterials(project.consumedMaterials());
            p.setPlacedByBlockId(project.placedByBlockId());
            p.setPlacementFailures(project.placementFailures());
            p.clearBuildSession();
            p.setStatus(BuildProject.Status.DONE);
        });
    }

    private static void notifyForceFinished(ServerPlayer player, String projectName) {
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new SitePreviewSyncPacket(
                        SitePreviewSyncPacket.Action.CLEAR,
                        null,
                        "",
                        false));
        player.sendSystemMessage(Component.translatable(
                "message.touhou_maid_construction.build_force_finished", projectName));
    }

    public static void syncProjectProgress(BuildJob job) {
        // Called from behavior via packet; project NBT updated on terminal reopen.
    }

    public static void syncIgnoredToJob(BuildJobKey key, BuildProject project) {
        BuildJobRegistry.get(key).ifPresent(job -> job.syncIgnoredBlocks(project.ignoredBlockIds()));
    }

    /**
     * After missing-block replacements change while paused: rebuild the in-memory job task list
     * so newly mapped cells become placeable; already-matched world cells stay placed.
     */
    public static void rebuildPausedJobForReplacements(ServerLevel level, BuildProject project) {
        if (project == null || project.status() != BuildProject.Status.PAUSED) {
            return;
        }
        Optional<BuildJobKey> keyOpt = project.buildJobKey();
        if (keyOpt.isEmpty() || project.anchor() == null) {
            return;
        }
        BuildJobKey key = keyOpt.get();
        BuildJobRegistry.get(key).ifPresent(old -> {
            MaterialSource materials = old.materials();
            BuildJobRegistry.remove(key);
            recreateJob(level, project, key, materials).ifPresent(job -> {
                job.setPaused(true, "missing_replace");
                project.setStatus(BuildProject.Status.PAUSED);
                project.setPlacedBlocks(job.placedCount());
                project.setConsumedMaterials(Math.max(project.consumedMaterials(), job.consumedMaterials()));
                project.setPlacedByBlockId(
                        BuildStartRescanPolicy.projectPlacedByBlockIdAfterRescan(
                                project.placedByBlockId(), job.placedByBlockId()));
            });
        });
    }

    public static void syncAutoCraftToJob(BuildJobKey key, BuildProject project) {
        BuildJobRegistry.get(key).ifPresent(job -> {
            AutoCraftingMaterialSource.setEnabled(job.materials(), project.autoCraftMaterials());
            if (project.autoCraftMaterials()) {
                // Clear unavailable blacklist so previously failed crafts can retry.
                StockIndexedMaterialSource.requestRebuild(job.materials());
            }
        });
    }

    private static MaterialSource withAutoCraft(
            MaterialSource materials,
            BuildProject project,
            ServerLevel level) {
        if (materials == null || project == null || level == null) {
            return materials;
        }
        return StockIndexedMaterialSource.wrap(AutoCraftingMaterialSource.wrap(
                materials,
                new AtomicBoolean(project.autoCraftMaterials()),
                level));
    }

    /** Sync terminal project data and broadcast progress after a placed block was destroyed. */
    public static void onPlacedBlockBroken(ServerLevel level, BuildJob job, BlockPos broken) {
        syncProgressAfterPlacement(level, job, broken);
    }

    /** Sync terminal project data and broadcast progress after any server-side placement. */
    public static void syncProgressAfterPlacement(ServerLevel level, BuildJob job, BlockPos placed) {
        updateProjectByKey(level, job.key(), project -> {
            project.setPlacedBlocks(job.placedCount());
            project.setConsumedMaterials(job.consumedMaterials());
            project.setPlacedByBlockId(job.placedByBlockId());
            if (job.isDone()) {
                project.setStatus(BuildProject.Status.DONE);
                project.setPlacementFailures(job.placementFailures());
            } else if (job.isPaused()) {
                project.setStatus(BuildProject.Status.PAUSED);
            } else {
                project.setStatus(BuildProject.Status.BUILDING);
            }
        });
        job.finishIfComplete(level);
        if (job.isDone()) {
            notifyJobComplete(job, level);
            return;
        }
        ModNetwork.CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                new BuildProgressPacket(
                        job.key(),
                        placed == null ? BlockPos.ZERO : placed,
                        job.placedCount(),
                        job.totalBlocks(),
                        job.consumedMaterials(),
                        job.isDone(),
                        job.isPaused(),
                        job.pauseReason(),
                        ConstructionMaidBinding.materialWaitsForJob(job.key()),
                        job.placedByBlockId(),
                        List.of()));
    }

    public static void creativeFillMaterials(
            ServerPlayer player,
            BuildProject project,
            BlockPos terminalPos,
            boolean itemMode,
            InteractionHand hand) {
        if (!player.isCreative() || project.schematicPath().isEmpty()) {
            return;
        }
        try {
            int filled;
            if (itemMode) {
                Optional<ItemStack> terminalStack =
                        ItemTerminalData.findTerminalStack(player, hand, project.id());
                if (terminalStack.isEmpty()) {
                    return;
                }
                ItemStack stack = terminalStack.get();
                NonNullList<ItemStack> items = ItemTerminalData.loadVaultItems(stack, player.getServer());
                // #region agent log
                {
                    com.google.gson.JsonObject start = VaultFillDebugLog.data();
                    start.addProperty("projectId", project.id());
                    start.addProperty("schematic", project.schematicPath());
                    start.addProperty("itemMode", true);
                    start.addProperty("hand", hand == null ? "null" : hand.name());
                    start.addProperty("resolvedStackHash", System.identityHashCode(stack));
                    start.addProperty("handStackHash", System.identityHashCode(player.getItemInHand(hand)));
                    start.addProperty("sameStackAsHand", stack == player.getItemInHand(hand));
                    VaultFillDebugLog.vaultSnapshot(start, "vaultBefore", items);
                    VaultFillDebugLog.log("H1-H2", "BuildJobService.creativeFillMaterials", "fill_start", start);
                }
                // #endregion
                filled = creativeFillVaultForProject(items, project);
                // #region agent log
                {
                    com.google.gson.JsonObject end = VaultFillDebugLog.data();
                    end.addProperty("projectId", project.id());
                    end.addProperty("filledTypes", filled);
                    VaultFillDebugLog.vaultSnapshot(end, "vaultAfterFill", items);
                    VaultFillDebugLog.log("H1-H2", "BuildJobService.creativeFillMaterials", "fill_before_save", end);
                }
                // #endregion
                ItemTerminalData.saveVaultItems(stack, items, player.getServer());
                player.getInventory().setChanged();
                // #region agent log
                {
                    com.google.gson.JsonObject saved = VaultFillDebugLog.data();
                    saved.addProperty("projectId", project.id());
                    VaultFillDebugLog.vaultSnapshot(saved, "vaultAfterSave", ItemTerminalData.loadVaultItems(stack, player.getServer()));
                    VaultFillDebugLog.log("H1", "BuildJobService.creativeFillMaterials", "fill_after_save", saved);
                }
                // #endregion
            } else {
                BlockEntity be = player.level().getBlockEntity(terminalPos);
                if (!(be instanceof ConstructionTerminalBE terminal)) {
                    return;
                }
                // #region agent log
                {
                    com.google.gson.JsonObject start = VaultFillDebugLog.data();
                    start.addProperty("projectId", project.id());
                    start.addProperty("schematic", project.schematicPath());
                    start.addProperty("itemMode", false);
                    start.addProperty("terminalPos", terminalPos.toShortString());
                    VaultFillDebugLog.vaultSnapshot(start, "vaultBefore", terminal);
                    VaultFillDebugLog.log("H1-H3", "BuildJobService.creativeFillMaterials", "fill_start", start);
                }
                // #endregion
                filled = creativeFillVaultForProject(terminal, project);
                terminal.setChanged();
                // #region agent log
                {
                    com.google.gson.JsonObject end = VaultFillDebugLog.data();
                    end.addProperty("projectId", project.id());
                    end.addProperty("filledTypes", filled);
                    VaultFillDebugLog.vaultSnapshot(end, "vaultAfterFill", terminal);
                    VaultFillDebugLog.log("H1-H3", "BuildJobService.creativeFillMaterials", "fill_end", end);
                }
                // #endregion
            }
            player.sendSystemMessage(Component.translatable(
                    "message.touhou_maid_construction.creative_fill_done", filled));
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Creative fill failed: " + e.getMessage()));
        }
    }

    private static int creativeFillVaultForProject(Container vault, BuildProject project)
            throws IOException {
        var summary = SchematicFiles.applyReplacements(
                SchematicFiles.summarize(
                        SchematicFiles.resolveSchematic(project.schematicPath()), project.startLayer()),
                project.missingBlockReplacements());
        return MaterialVault.fillFromLines(vault, summary.lines(), project);
    }

    private static int creativeFillVaultForProject(NonNullList<ItemStack> items, BuildProject project)
            throws IOException {
        var summary = SchematicFiles.applyReplacements(
                SchematicFiles.summarize(
                        SchematicFiles.resolveSchematic(project.schematicPath()), project.startLayer()),
                project.missingBlockReplacements());
        return MaterialVault.fillFromLines(items, summary.lines(), project);
    }

    /** Re-create in-memory jobs after world reload for terminals with BUILDING projects. */
    public static void restoreTerminalJobs(ServerLevel level, ConstructionTerminalBE terminal) {
        for (BuildProject project : terminal.getProjects()) {
            if (project.status() != BuildProject.Status.BUILDING) {
                continue;
            }
            if (!project.siteConfirmed() || project.anchor() == null) {
                continue;
            }
            Optional<BuildJobKey> savedKey = project.buildJobKey();
            BuildJobKey key;
            if (savedKey.isPresent()) {
                key = savedKey.get();
            } else {
                Optional<UUID> ownerId = resolveOwnerId(level, project);
                if (ownerId.isEmpty()) {
                    continue;
                }
                key = new BuildJobKey(
                        project.id(),
                        terminal.getBlockPos(),
                        false,
                        InteractionHand.MAIN_HAND,
                        ownerId.get());
            }
            if (BuildJobRegistry.get(key).isPresent()) {
                BuildJob existing = BuildJobRegistry.get(key).get();
                if (BuildJobResumePolicy.shouldKickoffRestoredJob(true, existing.isDone())) {
                    kickoffRestoredBuildingProject(level, project, key);
                } else {
                    markProjectForceFinished(project, existing);
                    BuildJobRegistry.remove(key);
                    ConstructionMaidFollow.endForJob(key, level);
                    ConstructionMaidBinding.clearJob(key);
                    terminal.setChanged();
                }
                continue;
            }
            Optional<BuildJob> job = recreateJob(level, project, key, new BlockTerminalMaterialSource(terminal));
            job.ifPresent(j -> {
                if (!BuildJobResumePolicy.shouldKickoffRestoredJob(true, j.isDone())) {
                    markProjectForceFinished(project, j);
                    BuildJobRegistry.remove(key);
                    ConstructionMaidFollow.endForJob(key, level);
                    ConstructionMaidBinding.clearJob(key);
                    terminal.setChanged();
                    return;
                }
                refreshProjectProgressFromJob(project, key, j);
                terminal.setChanged();
                kickoffRestoredBuildingProject(level, project, key);
            });
        }
    }

    /** Re-create item-terminal jobs when the owner logs in. */
    public static void restoreItemTerminalJobs(ServerPlayer player) {
        restoreItemStackJobs(player, player.getMainHandItem(), InteractionHand.MAIN_HAND);
        restoreItemStackJobs(player, player.getOffhandItem(), InteractionHand.OFF_HAND);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof ConstructionTerminalItem) {
                restoreProjectsOnStack(player, stack, InteractionHand.MAIN_HAND);
            }
        }
    }

    private static void restoreItemStackJobs(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        if (stack.getItem() instanceof ConstructionTerminalItem) {
            restoreProjectsOnStack(player, stack, hand);
        }
    }

    private static void restoreProjectsOnStack(ServerPlayer player, ItemStack stack, InteractionHand hand) {
        List<BuildProject> projects = ItemTerminalData.getProjects(stack);
        boolean changed = false;
        for (BuildProject project : projects) {
            if (project.status() != BuildProject.Status.BUILDING) {
                continue;
            }
            if (!project.siteConfirmed() || project.anchor() == null) {
                continue;
            }
            Optional<BuildJobKey> savedKey = project.buildJobKey();
            BuildJobKey key;
            if (savedKey.isPresent()) {
                key = savedKey.get();
            } else {
                key = new BuildJobKey(project.id(), BlockPos.ZERO, true, hand, player.getUUID());
            }
            if (BuildJobRegistry.get(key).isPresent()) {
                BuildJob existing = BuildJobRegistry.get(key).get();
                if (BuildJobResumePolicy.shouldKickoffRestoredJob(true, existing.isDone())) {
                    kickoffRestoredBuildingProject(player.serverLevel(), project, key);
                } else {
                    markProjectForceFinished(project, existing);
                    BuildJobRegistry.remove(key);
                    ConstructionMaidFollow.endForJob(key, player.serverLevel());
                    ConstructionMaidBinding.clearJob(key);
                }
                changed = true;
                continue;
            }
            Optional<BuildJob> job = recreateJob(
                    player.serverLevel(),
                    project,
                    key,
                    new ItemTerminalMaterialSource(player, hand, project.id()));
            if (job.isPresent()) {
                if (!BuildJobResumePolicy.shouldKickoffRestoredJob(true, job.get().isDone())) {
                    markProjectForceFinished(project, job.get());
                    BuildJobRegistry.remove(key);
                    ConstructionMaidFollow.endForJob(key, player.serverLevel());
                    ConstructionMaidBinding.clearJob(key);
                    changed = true;
                    continue;
                }
                refreshProjectProgressFromJob(project, key, job.get());
                changed = true;
                kickoffRestoredBuildingProject(player.serverLevel(), project, key);
            }
        }
        if (changed) {
            ItemTerminalData.saveProjects(stack, projects, ItemTerminalData.getSelectedId(stack));
        }
    }

    /**
     * Login-time bootstrap for block terminals around the player. This performs one default
     * "start" for BUILDING projects by restoring jobs and rebinding maids.
     */
    public static void restoreNearbyTerminalJobs(ServerLevel level, BlockPos center) {
        restoreTerminalJobsNear(level, center, TERMINAL_CHUNK_RADIUS);
    }

    /**
     * Force-restore every BUILDING session owned by the player: load terminal chunks if needed,
     * recreate in-memory jobs, then run the same maid assignment path as pressing Start.
     */
    public static void restoreActiveBuildingJobs(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Set<BuildJobKey> candidates = new LinkedHashSet<>(ConstructionPlayerBuildIndex.activeJobs(player));
        candidates.addAll(collectBuildingKeysFromItemTerminals(player));
        candidates.addAll(collectBuildingKeysFromNearbyTerminals(level, player.blockPosition(), player.getUUID()));
        for (EntityMaid maid : level.getEntitiesOfClass(
                EntityMaid.class, player.getBoundingBox().inflate(MAID_SEARCH_RADIUS * 2.0D))) {
            if (player.getUUID().equals(ownerId(maid))) {
                candidates.addAll(collectBuildingKeysFromNearbyTerminals(
                        level, maid.blockPosition(), player.getUUID()));
            }
        }
        int resumed = 0;
        if (!needsBuildingResume(player, candidates)) {
            LOGGER.info(
                    "[TMC-Recover] restoreActiveBuildingJobs skipped player={} candidates={} (already active)",
                    player.getUUID(),
                    candidates.size());
            return;
        }
        for (BuildJobKey key : candidates) {
            if (!key.ownerId().equals(player.getUUID())) {
                continue;
            }
            if (resumeBuildingJob(player, key)) {
                resumed++;
            }
        }
        LOGGER.info(
                "[TMC-Recover] restoreActiveBuildingJobs player={} candidates={} resumed={}",
                player.getUUID(),
                candidates.size(),
                resumed);
    }

    /** Re-kickoff all in-memory BUILDING jobs owned by the player after delayed login passes. */
    public static void resumeOwnerBuildingProjects(ServerLevel level, UUID ownerId, BlockPos center) {
        restoreTerminalJobsNear(level, center, TERMINAL_CHUNK_RADIUS);
        for (BuildJobKey key : BuildJobRegistry.all().keySet()) {
            if (!key.ownerId().equals(ownerId)) {
                continue;
            }
            Optional<BuildProject> project = findProject(level, key);
            if (project.isEmpty() || project.get().status() != BuildProject.Status.BUILDING) {
                continue;
            }
            kickoffRestoredBuildingProject(level, project.get(), key);
        }
    }

    private static boolean needsBuildingResume(ServerPlayer player, Set<BuildJobKey> candidates) {
        ServerLevel level = player.serverLevel();
        for (BuildJobKey key : candidates) {
            if (!key.ownerId().equals(player.getUUID())) {
                continue;
            }
            if (needsBuildingResumeForKey(player, level, key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean needsBuildingResumeForKey(
            ServerPlayer player, ServerLevel level, BuildJobKey key) {
        Optional<BuildProject> projectOpt = resolveProjectForRestore(player, level, key);
        boolean building = projectOpt.isPresent()
                && projectOpt.get().status() == BuildProject.Status.BUILDING;
        boolean jobPresent = BuildJobRegistry.get(key).isPresent();
        boolean maidMissingBind = false;
        if (building && jobPresent) {
            for (UUID maidId : projectOpt.get().assignedMaids()) {
                if (level.getEntity(maidId) instanceof EntityMaid maid
                        && ConstructionMaidBinding.jobOf(maid).isEmpty()) {
                    maidMissingBind = true;
                    break;
                }
            }
        }
        return BuildJobResumePolicy.needsResume(building, jobPresent, maidMissingBind);
    }

    private static boolean resumeBuildingJob(ServerPlayer player, BuildJobKey key) {
        ServerLevel level = player.serverLevel();
        Optional<BuildProject> projectOpt = resolveProjectForRestore(player, level, key);
        if (projectOpt.isEmpty()) {
            LOGGER.warn("[TMC-Recover] project missing for key={}", key);
            return false;
        }
        BuildProject project = projectOpt.get();
        if (project.status() != BuildProject.Status.BUILDING) {
            ConstructionPlayerBuildIndex.forget(player, key);
            return false;
        }
        if (!project.siteConfirmed() || project.anchor() == null) {
            return false;
        }
        MaterialSource materials = resolveMaterialsForRestore(player, level, key);
        if (materials == null) {
            LOGGER.warn("[TMC-Recover] material source missing for key={}", key);
            return false;
        }
        if (BuildJobRegistry.get(key).isEmpty()) {
            Optional<BuildJob> job = recreateJob(level, project, key, materials);
            if (job.isEmpty()) {
                return false;
            }
            // Stale BUILDING after finish (item NBT overwrite) or fully-synced world — finalize, no kickoff.
            if (!BuildJobResumePolicy.shouldKickoffRestoredJob(true, job.get().isDone())) {
                markProjectForceFinished(project, job.get());
                persistForceFinished(player, level, key, project);
                BuildJobRegistry.remove(key);
                ConstructionMaidFollow.endForJob(key, level);
                ConstructionMaidBinding.clearJob(key);
                ConstructionPlayerBuildIndex.forget(player, key);
                return false;
            }
            refreshProjectProgressFromJob(project, key, job.get());
            persistProjectAfterRestore(player, level, key, project);
            kickoffRestoredBuildingProject(level, project, key);
            ConstructionPlayerBuildIndex.remember(player, key);
            return true;
        }
        if (!needsBuildingResumeForKey(player, level, key)) {
            ConstructionPlayerBuildIndex.remember(player, key);
            return false;
        }
        kickoffRestoredBuildingProject(level, project, key);
        ConstructionPlayerBuildIndex.remember(player, key);
        return true;
    }

    private static Optional<BuildProject> resolveProjectForRestore(
            ServerPlayer player, ServerLevel level, BuildJobKey key) {
        if (key.itemMode()) {
            ItemStack stack = ItemTerminalData.findTerminalStack(player, key.hand(), key.projectId())
                    .orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                stack = player.getItemInHand(key.hand());
            }
            if (stack.getItem() instanceof ConstructionTerminalItem) {
                return BuildProject.find(ItemTerminalData.getProjects(stack), key.projectId());
            }
            return Optional.empty();
        }
        ConstructionChunkLoad.ensureLoaded(level, key.terminalPos());
        BlockEntity be = level.getBlockEntity(key.terminalPos());
        if (be instanceof ConstructionTerminalBE terminal) {
            return BuildProject.find(terminal.getProjects(), key.projectId());
        }
        return Optional.empty();
    }

    private static MaterialSource resolveMaterialsForRestore(
            ServerPlayer player, ServerLevel level, BuildJobKey key) {
        if (key.itemMode()) {
            return new ItemTerminalMaterialSource(player, key.hand(), key.projectId());
        }
        ConstructionChunkLoad.ensureLoaded(level, key.terminalPos());
        BlockEntity be = level.getBlockEntity(key.terminalPos());
        if (be instanceof ConstructionTerminalBE terminal) {
            return new BlockTerminalMaterialSource(terminal);
        }
        return null;
    }

    private static void persistProjectAfterRestore(
            ServerPlayer player, ServerLevel level, BuildJobKey key, BuildProject project) {
        if (key.itemMode()) {
            ItemStack stack = ItemTerminalData.findTerminalStack(player, key.hand(), key.projectId())
                    .orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                return;
            }
            List<BuildProject> projects = ItemTerminalData.getProjects(stack);
            BuildProject.find(projects, key.projectId()).ifPresent(found -> {
                found.setBuildSession(key);
                found.setStatus(BuildProject.Status.BUILDING);
                found.setPlacedBlocks(project.placedBlocks());
                found.setConsumedMaterials(project.consumedMaterials());
                found.setPlacedByBlockId(project.placedByBlockId());
            });
            ItemTerminalData.saveProjects(stack, projects, ItemTerminalData.getSelectedId(stack));
            return;
        }
        BlockEntity be = level.getBlockEntity(key.terminalPos());
        if (be instanceof ConstructionTerminalBE terminal) {
            BuildProject.find(terminal.getProjects(), key.projectId()).ifPresent(found -> {
                found.setBuildSession(key);
                found.setStatus(BuildProject.Status.BUILDING);
                found.setPlacedBlocks(project.placedBlocks());
                found.setConsumedMaterials(project.consumedMaterials());
                found.setPlacedByBlockId(project.placedByBlockId());
                terminal.setChanged();
            });
        }
    }

    private static List<BuildJobKey> collectBuildingKeysFromItemTerminals(ServerPlayer player) {
        List<BuildJobKey> keys = new ArrayList<>();
        collectBuildingKeysFromStack(player.getMainHandItem(), InteractionHand.MAIN_HAND, player.getUUID(), keys);
        collectBuildingKeysFromStack(player.getOffhandItem(), InteractionHand.OFF_HAND, player.getUUID(), keys);
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof ConstructionTerminalItem) {
                collectBuildingKeysFromStack(stack, InteractionHand.MAIN_HAND, player.getUUID(), keys);
            }
        }
        return keys;
    }

    private static void collectBuildingKeysFromStack(
            ItemStack stack, InteractionHand hand, UUID ownerId, List<BuildJobKey> keys) {
        if (!(stack.getItem() instanceof ConstructionTerminalItem)) {
            return;
        }
        for (BuildProject project : ItemTerminalData.getProjects(stack)) {
            if (project.status() != BuildProject.Status.BUILDING) {
                continue;
            }
            Optional<BuildJobKey> saved = project.buildJobKey();
            if (saved.isPresent()) {
                keys.add(saved.get());
            } else {
                keys.add(new BuildJobKey(project.id(), BlockPos.ZERO, true, hand, ownerId));
            }
        }
    }

    private static List<BuildJobKey> collectBuildingKeysFromNearbyTerminals(
            ServerLevel level, BlockPos center, UUID ownerId) {
        List<BuildJobKey> keys = new ArrayList<>();
        net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(center);
        for (int cx = chunkPos.x - TERMINAL_CHUNK_RADIUS; cx <= chunkPos.x + TERMINAL_CHUNK_RADIUS; cx++) {
            for (int cz = chunkPos.z - TERMINAL_CHUNK_RADIUS; cz <= chunkPos.z + TERMINAL_CHUNK_RADIUS; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!(be instanceof ConstructionTerminalBE terminal)) {
                        continue;
                    }
                    for (BuildProject project : terminal.getProjects()) {
                        if (project.status() != BuildProject.Status.BUILDING) {
                            continue;
                        }
                        if (project.buildOwnerId() != null && !project.buildOwnerId().equals(ownerId)) {
                            continue;
                        }
                        Optional<BuildJobKey> saved = project.buildJobKey();
                        if (saved.isPresent()) {
                            keys.add(saved.get());
                        } else {
                            keys.add(new BuildJobKey(
                                    project.id(),
                                    terminal.getBlockPos(),
                                    false,
                                    InteractionHand.MAIN_HAND,
                                    ownerId));
                        }
                    }
                }
            }
        }
        return keys;
    }

    /** Bind maids that lost in-memory job links after reload. */
    public static void tryBindMaid(ServerLevel level, EntityMaid maid) {
        if (ConstructionMaidBinding.jobOf(maid).isPresent()) {
            return;
        }
        IMaidTask construction = TaskManager.findTask(TaskConstruction.UID).orElse(null);
        if (construction == null) {
            return;
        }
        ServerPlayer owner = maid.getOwner() instanceof ServerPlayer player ? player : null;
        if (owner == null) {
            return;
        }
        restoreTerminalJobsNear(level, maid.blockPosition(), TERMINAL_CHUNK_RADIUS);
        for (BuildJobKey key : BuildJobRegistry.all().keySet()) {
            if (!key.ownerId().equals(owner.getUUID())) {
                continue;
            }
            Optional<BuildProject> project = findProject(level, key);
            if (project.isEmpty() || project.get().status() != BuildProject.Status.BUILDING) {
                continue;
            }
            if (!project.get().assignedMaids().contains(maid.getUUID())) {
                continue;
            }
            maid.setTask(construction);
            ConstructionMaidBinding.bind(maid, key);
            ConstructionMaidFollow.onConstructionStart(maid, BuildJobRegistry.get(key).orElse(null));
            return;
        }
    }

    private static void restoreTerminalJobsNear(ServerLevel level, BlockPos center, int chunkRadius) {
        net.minecraft.world.level.ChunkPos chunkPos = new net.minecraft.world.level.ChunkPos(center);
        for (int cx = chunkPos.x - chunkRadius; cx <= chunkPos.x + chunkRadius; cx++) {
            for (int cz = chunkPos.z - chunkRadius; cz <= chunkPos.z + chunkRadius; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof ConstructionTerminalBE terminal) {
                        restoreTerminalJobs(level, terminal);
                    }
                }
            }
        }
    }

    private static Optional<BuildProject> findProject(ServerLevel level, BuildJobKey key) {
        if (key.itemMode()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(key.ownerId());
            if (player == null) {
                return Optional.empty();
            }
            ItemStack stack = player.getItemInHand(key.hand());
            return BuildProject.find(ItemTerminalData.getProjects(stack), key.projectId());
        }
        BlockEntity be = level.getBlockEntity(key.terminalPos());
        if (be instanceof ConstructionTerminalBE terminal) {
            return BuildProject.find(terminal.getProjects(), key.projectId());
        }
        return Optional.empty();
    }

    private static Optional<UUID> resolveOwnerId(ServerLevel level, BuildProject project) {
        if (project.buildOwnerId() != null) {
            return Optional.of(project.buildOwnerId());
        }
        for (UUID maidId : project.assignedMaids()) {
            EntityMaid maid = level.getEntity(maidId) instanceof EntityMaid m ? m : null;
            if (maid != null && maid.getOwner() != null) {
                return Optional.of(maid.getOwner().getUUID());
            }
        }
        return Optional.empty();
    }

    private static Optional<BuildJob> recreateJob(
            ServerLevel level,
            BuildProject project,
            BuildJobKey key,
            MaterialSource materials) {
        try {
            Blueprint blueprint = LitematicReader.read(
                    SchematicFiles.resolveSchematic(project.schematicPath()));
            List<BuildTask> tasks = BlueprintTaskBuilder.build(
                    blueprint,
                    project.anchor(),
                    project.placementYaw(),
                    project.startLayer(),
                    project.missingBlockReplacements());
            if (tasks.isEmpty()) {
                return Optional.empty();
            }
            materials = withAutoCraft(materials, project, level);
            BuildJob job = new BuildJob(
                    key, blueprint, project.anchor(), materials, tasks, project.ignoredBlockIds(),
                    project.placementYaw(), project.coverTerrain(), project.ignoreOverlaps());
            job.syncAlreadyBuilt(level);
            // Repair rescan: world match owns the queue. Inflating placedByBlockId from NBT then
            // reconcileStalePending would skip real missing cells (see BuildStartRescanPolicy).
            if (BuildStartRescanPolicy.allowSkipPendingFromSavedCounts(true)) {
                job.restorePlacedProgress(project.placedByBlockId());
                job.reconcileStalePending(level);
            }
            job.restoreConsumedMaterials(project.consumedMaterials());
            BuildJobRegistry.register(job);
            return Optional.of(job);
        } catch (Exception e) {
            LOGGER.error("[TMC-Recover] recreateJob failed project={} terminal={}", key.projectId(), key.terminalPos(), e);
            return Optional.empty();
        }
    }

    private static void refreshProjectProgressFromJob(BuildProject project, BuildJobKey key, BuildJob job) {
        project.setBuildSession(key);
        project.setStatus(BuildProject.Status.BUILDING);
        project.setPlacedBlocks(job.placedCount());
        project.setConsumedMaterials(Math.max(project.consumedMaterials(), job.consumedMaterials()));
        project.setPlacedByBlockId(
                BuildStartRescanPolicy.projectPlacedByBlockIdAfterRescan(
                        project.placedByBlockId(), job.placedByBlockId()));
    }

    /** World rescan once: missing non-ignored cells return to pending for normal maid placement. */
    private static void rescanMissingForRepair(
            ServerLevel level, BuildProject project, BuildJobKey key, BuildJob job) {
        job.syncIgnoredBlocks(project.ignoredBlockIds());
        job.syncAlreadyBuilt(level);
        refreshProjectProgressFromJob(project, key, job);
    }

    /**
     * Emulates one default "start build" on world re-entry for projects that were already
     * BUILDING: resume the in-memory job and re-assign maids to construction task.
     */
    private static void kickoffRestoredBuildingProject(ServerLevel level, BuildProject project, BuildJobKey key) {
        BuildJobRegistry.get(key).ifPresent(job -> {
            job.resumeFromPause();
            rescanMissingForRepair(level, project, key, job);
            rebindAssignedMaids(level, project, key);
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(key.ownerId());
            if (owner != null) {
                ConstructionPlayerBuildIndex.remember(owner, key);
            }
        });
    }

    private static void rebindAssignedMaids(ServerLevel level, BuildProject project, BuildJobKey key) {
        IMaidTask construction = TaskManager.findTask(TaskConstruction.UID).orElse(null);
        if (construction == null) {
            return;
        }
        for (UUID maidId : project.assignedMaids()) {
            if (!(level.getEntity(maidId) instanceof EntityMaid maid)) {
                continue;
            }
            if (ConstructionMaidBinding.jobOf(maid).isPresent()) {
                continue;
            }
            maid.setTask(construction);
            ConstructionMaidBinding.bind(maid, key);
            ConstructionMaidFollow.onConstructionStart(maid, BuildJobRegistry.get(key).orElse(null));
        }
    }

    private static List<EntityMaid> assignConstructionMaids(
            ServerPlayer player, BuildProject project, BuildJobKey key) {
        List<EntityMaid> maids = resolveMaids(player, project, player.serverLevel());
        IMaidTask construction = TaskManager.findTask(TaskConstruction.UID).orElse(null);
        if (construction == null) {
            return maids;
        }
        for (EntityMaid maid : maids) {
            maid.setTask(construction);
            ConstructionMaidBinding.bind(maid, key);
            ConstructionMaidFollow.onConstructionStart(maid, BuildJobRegistry.get(key).orElse(null));
        }
        if (!maids.isEmpty()) {
            LOGGER.info(
                    "[TMC-Build] bind maids={} project={} paused={}",
                    maids.size(),
                    key.projectId(),
                    BuildJobRegistry.get(key).map(BuildJob::isPaused).orElse(true));
        }
        return maids;
    }

    private static List<EntityMaid> resolveMaids(ServerPlayer player, BuildProject project, ServerLevel level) {
        List<UUID> selected = project.assignedMaids();
        List<EntityMaid> result = new ArrayList<>();
        for (UUID id : selected) {
            findOwnedMaid(level, player, id).ifPresent(result::add);
        }
        return result;
    }

    private static Optional<EntityMaid> findOwnedMaid(ServerLevel level, ServerPlayer player, UUID maidId) {
        return level.getEntitiesOfClass(EntityMaid.class, player.getBoundingBox().inflate(MAID_SEARCH_RADIUS))
                .stream()
                .filter(m -> m.getUUID().equals(maidId) && player.getUUID().equals(ownerId(m)))
                .findFirst();
    }

    private static UUID ownerId(EntityMaid maid) {
        LivingEntity owner = maid.getOwner();
        return owner == null ? null : owner.getUUID();
    }

    public static List<MaidInfo> listNearbyMaids(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        List<MaidInfo> list = new ArrayList<>();
        for (EntityMaid maid : level.getEntitiesOfClass(EntityMaid.class, player.getBoundingBox().inflate(MAID_SEARCH_RADIUS))) {
            if (!player.getUUID().equals(ownerId(maid))) {
                continue;
            }
            String name = maid.getName().getString();
            boolean onTask = maid.getTask().getUid().equals(TaskConstruction.UID);
            String materialWait = ConstructionMaidBinding.materialWaitBlockId(maid.getUUID()).orElse("");
            list.add(new MaidInfo(maid.getUUID(), name, onTask, materialWait));
        }
        list.sort(Comparator.comparing(MaidInfo::name));
        return list;
    }

    static Optional<String> resolveProjectName(ServerLevel level, BuildJobKey key) {
        if (level == null || key == null) {
            return Optional.empty();
        }
        if (key.itemMode()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(key.ownerId());
            if (player == null) {
                return Optional.empty();
            }
            return ItemTerminalData.findTerminalStack(player, key.hand(), key.projectId())
                    .flatMap(stack -> BuildProject.find(ItemTerminalData.getProjects(stack), key.projectId())
                            .map(BuildProject::name));
        }
        BlockEntity be = level.getBlockEntity(key.terminalPos());
        if (be instanceof ConstructionTerminalBE terminal) {
            return BuildProject.find(terminal.getProjects(), key.projectId()).map(BuildProject::name);
        }
        return Optional.empty();
    }

    public static void updateProjectByKey(ServerLevel level, BuildJobKey key, java.util.function.Consumer<BuildProject> edit) {
        if (key.itemMode()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(key.ownerId());
            if (player == null) {
                return;
            }
            ItemStack stack = ItemTerminalData.findTerminalStack(player, key.hand(), key.projectId())
                    .orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                return;
            }
            List<BuildProject> projects = ItemTerminalData.getProjects(stack);
            BuildProject.find(projects, key.projectId()).ifPresent(p -> {
                edit.accept(p);
                ItemTerminalData.saveProjects(stack, projects, ItemTerminalData.getSelectedId(stack));
            });
            return;
        }
        BlockEntity be = level.getBlockEntity(key.terminalPos());
        if (be instanceof ConstructionTerminalBE terminal) {
            BuildProject.find(terminal.getProjects(), key.projectId()).ifPresent(p -> {
                edit.accept(p);
                terminal.setChanged();
            });
        }
    }

    /**
     * Drop in-memory job and maid bindings when a project is deleted
     * or its terminal is broken while still BUILDING / PAUSED.
     */
    public static void cleanupDeletedProject(ServerLevel level, BuildProject project, BuildJobKey fallbackKey) {
        BuildJobKey key = project.buildJobKey().orElse(fallbackKey);
        ConstructionMaidFollow.endForJob(key, level);
        BuildJobRegistry.remove(key);
        ConstructionMaidBinding.clearJob(key);
        project.clearBuildSession();
        if (project.status() == BuildProject.Status.BUILDING
                || project.status() == BuildProject.Status.PAUSED) {
            project.setStatus(BuildProject.Status.SITE_READY);
        }
    }

    /** Drop in-memory job state when a block terminal is removed (NBT may still restore later). */
    public static void cleanupTerminalProjects(ServerLevel level, ConstructionTerminalBE terminal) {
        for (BuildProject project : terminal.getProjects()) {
            if (project.status() != BuildProject.Status.BUILDING
                    && project.status() != BuildProject.Status.PAUSED) {
                continue;
            }
            BuildJobKey key = project.buildJobKey().orElseGet(() -> new BuildJobKey(
                    project.id(),
                    terminal.getBlockPos(),
                    false,
                    InteractionHand.MAIN_HAND,
                    project.buildOwnerId() != null
                            ? project.buildOwnerId()
                            : resolveOwnerId(level, project).orElse(new UUID(0L, 0L))));
            // Keep BUILDING/PAUSED + session in NBT so pickup-as-item / re-place can restore the job.
            ConstructionMaidFollow.endForJob(key, level);
            BuildJobRegistry.remove(key);
            ConstructionMaidBinding.clearJob(key);
        }
    }

    public record MaidInfo(UUID id, String name, boolean onConstructionTask, String materialWaitBlockId) {
    }
}
