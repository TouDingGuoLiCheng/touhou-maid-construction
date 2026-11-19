package com.touhoumaid.construction.network;

import com.touhoumaid.construction.block.entity.ConstructionTerminalBE;
import com.touhoumaid.construction.build.BlockTerminalMaterialSource;
import com.touhoumaid.construction.build.BuildJobKey;
import com.touhoumaid.construction.build.BuildJobRegistry;
import com.touhoumaid.construction.build.BuildJobService;
import com.touhoumaid.construction.build.ItemTerminalMaterialSource;
import com.touhoumaid.construction.item.ConstructionTerminalItem;
import com.touhoumaid.construction.item.ItemTerminalData;
import com.touhoumaid.construction.project.BuildProject;
import com.touhoumaid.construction.project.MissingBlockReplacePolicy;
import com.touhoumaid.construction.project.SchematicFiles;
import com.touhoumaid.construction.project.VaultCounts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;

import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** C2S actions from the construction hub GUI (placed block or held item). */
public final class TerminalActionPacket {
    public enum Action {
        SELECT_PROJECT,
        IMPORT_SCHEMATIC,
        DELETE_PROJECT,
        SET_COVER_TERRAIN,
        SET_IGNORE_OVERLAPS,
        SET_START_LAYER,
        SET_AUTO_CRAFT,
        ENTER_SITE_PICK,
        OPEN_MATERIALS,
        START_BUILD,
        PAUSE_BUILD,
        FINISH_BUILD,
        TOGGLE_MAID,
        REFRESH,
        IGNORE_MATERIAL,
        RESTORE_MATERIAL,
        CREATIVE_FILL_MATERIALS,
        SET_MISSING_REPLACE,
        CLEAR_MISSING_REPLACE
    }

    private final BlockPos pos;
    private final Action action;
    private final String stringArg;
    private final boolean boolArg;
    private final boolean itemMode;
    private final InteractionHand hand;

    public TerminalActionPacket(
            BlockPos pos,
            Action action,
            String stringArg,
            boolean boolArg,
            boolean itemMode,
            InteractionHand hand) {
        this.pos = pos;
        this.action = action;
        this.stringArg = stringArg == null ? "" : stringArg;
        this.boolArg = boolArg;
        this.itemMode = itemMode;
        this.hand = hand == null ? InteractionHand.MAIN_HAND : hand;
    }

    private static TerminalActionPacket block(BlockPos pos, Action action, String arg, boolean flag) {
        return new TerminalActionPacket(pos, action, arg, flag, false, InteractionHand.MAIN_HAND);
    }

    private static TerminalActionPacket item(InteractionHand hand, Action action, String arg, boolean flag) {
        return new TerminalActionPacket(BlockPos.ZERO, action, arg, flag, true, hand);
    }

    public static TerminalActionPacket select(BlockPos pos, String projectId) {
        return block(pos, Action.SELECT_PROJECT, projectId, false);
    }

    public static TerminalActionPacket selectItem(InteractionHand hand, String projectId) {
        return item(hand, Action.SELECT_PROJECT, projectId, false);
    }

    public static TerminalActionPacket importSchematic(BlockPos pos, String fileName) {
        return block(pos, Action.IMPORT_SCHEMATIC, fileName, false);
    }

    public static TerminalActionPacket importSchematicItem(InteractionHand hand, String fileName) {
        return item(hand, Action.IMPORT_SCHEMATIC, fileName, false);
    }

    public static TerminalActionPacket delete(BlockPos pos, String projectId) {
        return block(pos, Action.DELETE_PROJECT, projectId, false);
    }

    public static TerminalActionPacket deleteItem(InteractionHand hand, String projectId) {
        return item(hand, Action.DELETE_PROJECT, projectId, false);
    }

    public static TerminalActionPacket cover(BlockPos pos, boolean enabled) {
        return block(pos, Action.SET_COVER_TERRAIN, "", enabled);
    }

    public static TerminalActionPacket coverItem(InteractionHand hand, boolean enabled) {
        return item(hand, Action.SET_COVER_TERRAIN, "", enabled);
    }

    public static TerminalActionPacket ignoreOverlaps(BlockPos pos, boolean enabled) {
        return block(pos, Action.SET_IGNORE_OVERLAPS, "", enabled);
    }

    public static TerminalActionPacket ignoreOverlapsItem(InteractionHand hand, boolean enabled) {
        return item(hand, Action.SET_IGNORE_OVERLAPS, "", enabled);
    }

    public static TerminalActionPacket startLayer(BlockPos pos, int layer) {
        return block(pos, Action.SET_START_LAYER, Integer.toString(Math.max(0, layer)), false);
    }

    public static TerminalActionPacket startLayerItem(InteractionHand hand, int layer) {
        return item(hand, Action.SET_START_LAYER, Integer.toString(Math.max(0, layer)), false);
    }

    public static TerminalActionPacket autoCraft(BlockPos pos, boolean enabled) {
        return block(pos, Action.SET_AUTO_CRAFT, "", enabled);
    }

    public static TerminalActionPacket autoCraftItem(InteractionHand hand, boolean enabled) {
        return item(hand, Action.SET_AUTO_CRAFT, "", enabled);
    }

    public static TerminalActionPacket enterSitePick(BlockPos pos) {
        return block(pos, Action.ENTER_SITE_PICK, "", false);
    }

    public static TerminalActionPacket enterSitePickItem(InteractionHand hand) {
        return item(hand, Action.ENTER_SITE_PICK, "", false);
    }

    public static TerminalActionPacket openMaterials(BlockPos pos) {
        return block(pos, Action.OPEN_MATERIALS, "", false);
    }

    public static TerminalActionPacket openMaterialsItem(InteractionHand hand) {
        return item(hand, Action.OPEN_MATERIALS, "", false);
    }

    public static TerminalActionPacket start(BlockPos pos) {
        return block(pos, Action.START_BUILD, "", false);
    }

    public static TerminalActionPacket startItem(InteractionHand hand) {
        return item(hand, Action.START_BUILD, "", false);
    }

    public static TerminalActionPacket pause(BlockPos pos) {
        return block(pos, Action.PAUSE_BUILD, "", false);
    }

    public static TerminalActionPacket pauseItem(InteractionHand hand) {
        return item(hand, Action.PAUSE_BUILD, "", false);
    }

    public static TerminalActionPacket finish(BlockPos pos) {
        return block(pos, Action.FINISH_BUILD, "", false);
    }

    public static TerminalActionPacket finishItem(InteractionHand hand) {
        return item(hand, Action.FINISH_BUILD, "", false);
    }

    public static TerminalActionPacket toggleMaid(BlockPos pos, String maidId) {
        return block(pos, Action.TOGGLE_MAID, maidId, false);
    }

    public static TerminalActionPacket toggleMaidItem(InteractionHand hand, String maidId) {
        return item(hand, Action.TOGGLE_MAID, maidId, false);
    }

    public static TerminalActionPacket refresh(BlockPos pos) {
        return block(pos, Action.REFRESH, "", false);
    }

    public static TerminalActionPacket refreshItem(InteractionHand hand) {
        return item(hand, Action.REFRESH, "", false);
    }

    public static TerminalActionPacket ignoreMaterial(BlockPos pos, String blockId) {
        return block(pos, Action.IGNORE_MATERIAL, blockId, false);
    }

    public static TerminalActionPacket ignoreMaterialItem(InteractionHand hand, String blockId) {
        return item(hand, Action.IGNORE_MATERIAL, blockId, false);
    }

    public static TerminalActionPacket restoreMaterial(BlockPos pos, String blockId) {
        return block(pos, Action.RESTORE_MATERIAL, blockId, false);
    }

    public static TerminalActionPacket restoreMaterialItem(InteractionHand hand, String blockId) {
        return item(hand, Action.RESTORE_MATERIAL, blockId, false);
    }

    public static TerminalActionPacket creativeFill(BlockPos pos) {
        return block(pos, Action.CREATIVE_FILL_MATERIALS, "", false);
    }

    public static TerminalActionPacket creativeFillItem(InteractionHand hand) {
        return item(hand, Action.CREATIVE_FILL_MATERIALS, "", false);
    }

    public static TerminalActionPacket setMissingReplace(BlockPos pos, String missingId, String replacementId) {
        return block(pos, Action.SET_MISSING_REPLACE, missingId + "|" + replacementId, false);
    }

    public static TerminalActionPacket setMissingReplaceItem(
            InteractionHand hand, String missingId, String replacementId) {
        return item(hand, Action.SET_MISSING_REPLACE, missingId + "|" + replacementId, false);
    }

    public static TerminalActionPacket clearMissingReplace(BlockPos pos, String missingId) {
        return block(pos, Action.CLEAR_MISSING_REPLACE, missingId, false);
    }

    public static TerminalActionPacket clearMissingReplaceItem(InteractionHand hand, String missingId) {
        return item(hand, Action.CLEAR_MISSING_REPLACE, missingId, false);
    }

    public static void encode(TerminalActionPacket packet, FriendlyByteBuf buf) {
        buf.writeBlockPos(packet.pos);
        buf.writeEnum(packet.action);
        buf.writeUtf(packet.stringArg);
        buf.writeBoolean(packet.boolArg);
        buf.writeBoolean(packet.itemMode);
        buf.writeEnum(packet.hand);
    }

    public static TerminalActionPacket decode(FriendlyByteBuf buf) {
        return new TerminalActionPacket(
                buf.readBlockPos(),
                buf.readEnum(Action.class),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readEnum(InteractionHand.class));
    }

    public static void handle(TerminalActionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) {
                return;
            }
            if (packet.itemMode) {
                handleItem(player, packet);
            } else {
                handleBlock(player, packet);
            }
        });
        context.setPacketHandled(true);
    }

    private static void handleBlock(ServerPlayer player, TerminalActionPacket packet) {
        BlockEntity be = player.level().getBlockEntity(packet.pos);
        if (!(be instanceof ConstructionTerminalBE terminal) || !terminal.stillValid(player)) {
            return;
        }
        switch (packet.action) {
            case SELECT_PROJECT -> terminal.setSelectedProjectId(packet.stringArg);
            case IMPORT_SCHEMATIC -> importSchematic(player, terminal, packet.stringArg);
            case DELETE_PROJECT -> {
                BuildProject.find(terminal.getProjects(), packet.stringArg).ifPresent(p -> {
                    BuildJobKey key = BuildJobService.jobKeyFor(
                            player, p, terminal.getBlockPos(), false, InteractionHand.MAIN_HAND);
                    BuildJobService.cleanupDeletedProject(player.serverLevel(), p, key);
                });
                terminal.removeProject(packet.stringArg);
            }
            case SET_COVER_TERRAIN -> terminal.getSelectedProject().ifPresent(p -> {
                p.setCoverTerrain(packet.boolArg);
                terminal.setChanged();
            });
            case SET_IGNORE_OVERLAPS -> terminal.getSelectedProject().ifPresent(p -> {
                p.setIgnoreOverlaps(packet.boolArg);
                terminal.setChanged();
            });
            case SET_START_LAYER -> terminal.getSelectedProject().ifPresent(p -> {
                if (applyStartLayer(player, p, packet.stringArg)) {
                    terminal.setChanged();
                }
            });
            case SET_AUTO_CRAFT -> terminal.getSelectedProject().ifPresent(p -> {
                p.setAutoCraftMaterials(packet.boolArg);
                terminal.setChanged();
                BuildJobKey key = p.buildJobKey().orElseGet(() -> new BuildJobKey(
                        p.id(), terminal.getBlockPos(), false, InteractionHand.MAIN_HAND, player.getUUID()));
                BuildJobService.syncAutoCraftToJob(key, p);
            });
            case ENTER_SITE_PICK -> {
                BuildProject project = terminal.getSelectedProject().orElse(null);
                if (project == null || project.schematicPath().isEmpty()) {
                    player.sendSystemMessage(Component.translatable(
                            "message.touhou_maid_construction.pick_first"));
                    reopen(player, terminal);
                    return;
                }
                BuildJobKey siteKey = BuildJobService.jobKeyFor(
                        player, project, terminal.getBlockPos(), false, InteractionHand.MAIN_HAND);
                BuildJobService.pauseForSitePick(player.serverLevel(), project, siteKey);
                terminal.setChanged();
                ModNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new StartSitePickPacket(
                                terminal.getBlockPos(),
                                project.id(),
                                project.schematicPath(),
                                project.coverTerrain(),
                                project.ignoreOverlaps(),
                                project.startLayer(),
                                false,
                                InteractionHand.MAIN_HAND));
                return;
            }
            case OPEN_MATERIALS -> {
                NetworkHooks.openScreen(player, terminal, buf -> {
                    buf.writeBoolean(false);
                    buf.writeBlockPos(packet.pos);
                });
                return;
            }
            case START_BUILD -> terminal.getSelectedProject().ifPresent(p -> startBuild(player, p, terminal));
            case PAUSE_BUILD -> terminal.getSelectedProject().ifPresent(p -> pauseBuild(player, p, terminal));
            case FINISH_BUILD -> terminal.getSelectedProject().ifPresent(p -> finishBuild(player, p, terminal));
            case TOGGLE_MAID -> terminal.getSelectedProject().ifPresent(p -> {
                toggleMaid(player, p, packet.stringArg);
                terminal.setChanged();
            });
            case REFRESH -> {
            }
            case IGNORE_MATERIAL -> terminal.getSelectedProject().ifPresent(p -> {
                p.ignoreBlock(packet.stringArg);
                terminal.setChanged();
                BuildJobKey key = p.buildJobKey().orElseGet(() -> new BuildJobKey(
                        p.id(), terminal.getBlockPos(), false, InteractionHand.MAIN_HAND, player.getUUID()));
                BuildJobService.syncIgnoredToJob(key, p);
            });
            case RESTORE_MATERIAL -> terminal.getSelectedProject().ifPresent(p -> {
                p.restoreIgnoredBlock(packet.stringArg);
                terminal.setChanged();
                BuildJobKey key = p.buildJobKey().orElseGet(() -> new BuildJobKey(
                        p.id(), terminal.getBlockPos(), false, InteractionHand.MAIN_HAND, player.getUUID()));
                BuildJobService.syncIgnoredToJob(key, p);
            });
            case CREATIVE_FILL_MATERIALS -> terminal.getSelectedProject().ifPresent(p ->
                    BuildJobService.creativeFillMaterials(
                            player, p, terminal.getBlockPos(), false, InteractionHand.MAIN_HAND));
            case SET_MISSING_REPLACE -> terminal.getSelectedProject().ifPresent(p -> {
                applyMissingReplace(player, p, packet.stringArg, true);
                terminal.setChanged();
            });
            case CLEAR_MISSING_REPLACE -> terminal.getSelectedProject().ifPresent(p -> {
                applyMissingReplaceClear(player, p, packet.stringArg, true);
                terminal.setChanged();
            });
        }
        reopen(player, terminal);
    }

    private static void handleItem(ServerPlayer player, TerminalActionPacket packet) {
        ItemStack stack = player.getItemInHand(packet.hand);
        if (!(stack.getItem() instanceof ConstructionTerminalItem)) {
            return;
        }
        List<BuildProject> projects = ItemTerminalData.getProjects(stack);
        String selectedId = ItemTerminalData.getSelectedId(stack);
        switch (packet.action) {
            case SELECT_PROJECT -> {
                selectedId = packet.stringArg;
                ItemTerminalData.saveProjects(stack, projects, selectedId);
            }
            case IMPORT_SCHEMATIC -> {
                importSchematicToList(player, projects, packet.stringArg);
                if (!projects.isEmpty()) {
                    selectedId = projects.get(projects.size() - 1).id();
                }
                ItemTerminalData.saveProjects(stack, projects, selectedId);
            }
            case DELETE_PROJECT -> {
                BuildProject.find(projects, packet.stringArg).ifPresent(p -> {
                    BuildJobKey key = BuildJobService.jobKeyFor(
                            player, p, BlockPos.ZERO, true, packet.hand);
                    BuildJobService.cleanupDeletedProject(player.serverLevel(), p, key);
                });
                projects.removeIf(p -> p.id().equals(packet.stringArg));
                if (packet.stringArg.equals(selectedId)) {
                    selectedId = projects.isEmpty() ? "" : projects.get(0).id();
                }
                ItemTerminalData.saveProjects(stack, projects, selectedId);
            }
            case SET_COVER_TERRAIN -> {
                BuildProject.find(projects, selectedId).ifPresent(p -> p.setCoverTerrain(packet.boolArg));
                ItemTerminalData.saveProjects(stack, projects, selectedId);
            }
            case SET_IGNORE_OVERLAPS -> {
                BuildProject.find(projects, selectedId).ifPresent(p -> p.setIgnoreOverlaps(packet.boolArg));
                ItemTerminalData.saveProjects(stack, projects, selectedId);
            }
            case SET_START_LAYER -> {
                BuildProject.find(projects, selectedId).ifPresent(p -> applyStartLayer(player, p, packet.stringArg));
                ItemTerminalData.saveProjects(stack, projects, selectedId);
            }
            case SET_AUTO_CRAFT -> {
                BuildProject.find(projects, selectedId).ifPresent(p -> {
                    p.setAutoCraftMaterials(packet.boolArg);
                    BuildJobKey key = p.buildJobKey().orElseGet(() -> new BuildJobKey(
                            p.id(), BlockPos.ZERO, true, packet.hand, player.getUUID()));
                    BuildJobService.syncAutoCraftToJob(key, p);
                });
                ItemTerminalData.saveProjects(stack, projects, selectedId);
            }
            case ENTER_SITE_PICK -> {
                BuildProject project = BuildProject.find(projects, selectedId).orElse(null);
                if (project == null || project.schematicPath().isEmpty()) {
                    player.sendSystemMessage(Component.translatable(
                            "message.touhou_maid_construction.pick_first"));
                    reopenItem(player, packet.hand);
                    return;
                }
                BuildJobKey siteKey = BuildJobService.jobKeyFor(
                        player, project, BlockPos.ZERO, true, packet.hand);
                BuildJobService.pauseForSitePick(player.serverLevel(), project, siteKey);
                ItemTerminalData.saveProjects(stack, projects, selectedId);
                ModNetwork.CHANNEL.send(
                        PacketDistributor.PLAYER.with(() -> player),
                        new StartSitePickPacket(
                                BlockPos.ZERO,
                                project.id(),
                                project.schematicPath(),
                                project.coverTerrain(),
                                project.ignoreOverlaps(),
                                project.startLayer(),
                                true,
                                packet.hand));
                return;
            }
            case OPEN_MATERIALS -> {
                ConstructionTerminalItem.openItemMenu(player, stack, packet.hand);
                return;
            }
            case START_BUILD -> {
                Optional<BuildProject> project = BuildProject.find(projects, selectedId);
                if (project.isEmpty()) {
                    break;
                }
                BuildProject p = project.get();
                if (BuildJobService.start(
                        player, p, BlockPos.ZERO, true, packet.hand,
                        new ItemTerminalMaterialSource(player, packet.hand, p.id()))) {
                    ItemTerminalData.saveProjects(stack, projects, selectedId);
                }
            }
            case PAUSE_BUILD -> {
                Optional<BuildProject> paused = BuildProject.find(projects, selectedId);
                if (paused.isPresent()) {
                    BuildProject p = paused.get();
                    BuildJobKey key = p.buildJobKey().orElseGet(() -> new BuildJobKey(
                            p.id(), BlockPos.ZERO, true, packet.hand, player.getUUID()));
                    BuildJobService.pause(player.serverLevel(), p, key);
                    ItemTerminalData.saveProjects(stack, projects, selectedId);
                    player.sendSystemMessage(Component.translatable(
                            "message.touhou_maid_construction.build_paused"));
                }
            }
            case FINISH_BUILD -> {
                Optional<BuildProject> finishing = BuildProject.find(projects, selectedId);
                if (finishing.isPresent()) {
                    BuildProject p = finishing.get();
                    BuildJobKey key = p.buildJobKey().orElseGet(() -> new BuildJobKey(
                            p.id(), BlockPos.ZERO, true, packet.hand, player.getUUID()));
                    if (BuildJobService.forceFinish(player, p, key)) {
                        ItemTerminalData.saveProjects(stack, projects, selectedId);
                    }
                }
            }
            case TOGGLE_MAID -> {
                BuildProject.find(projects, selectedId).ifPresent(p -> toggleMaid(player, p, packet.stringArg));
                ItemTerminalData.saveProjects(stack, projects, selectedId);
            }
            case REFRESH -> {
            }
            case IGNORE_MATERIAL -> {
                BuildProject.find(projects, selectedId).ifPresent(p -> {
                    p.ignoreBlock(packet.stringArg);
                    BuildJobKey key = p.buildJobKey().orElseGet(() -> new BuildJobKey(
                            p.id(), BlockPos.ZERO, true, packet.hand, player.getUUID()));
                    BuildJobService.syncIgnoredToJob(key, p);
                });
                ItemTerminalData.saveProjects(stack, projects, selectedId);
            }
            case RESTORE_MATERIAL -> {
                BuildProject.find(projects, selectedId).ifPresent(p -> {
                    p.restoreIgnoredBlock(packet.stringArg);
                    BuildJobKey key = p.buildJobKey().orElseGet(() -> new BuildJobKey(
                            p.id(), BlockPos.ZERO, true, packet.hand, player.getUUID()));
                    BuildJobService.syncIgnoredToJob(key, p);
                });
                ItemTerminalData.saveProjects(stack, projects, selectedId);
            }
            case CREATIVE_FILL_MATERIALS -> {
                // #region agent log
                {
                    com.google.gson.JsonObject pre = com.touhoumaid.construction.build.VaultFillDebugLog.data();
                    pre.addProperty("selectedId", selectedId);
                    pre.addProperty("hand", packet.hand.name());
                    pre.addProperty("packetHandStackHash", System.identityHashCode(stack));
                    com.touhoumaid.construction.build.VaultFillDebugLog.vaultSnapshot(
                            pre, "vaultOnPacketEntry", ItemTerminalData.loadVaultItems(stack, player.getServer()));
                    com.touhoumaid.construction.build.VaultFillDebugLog.log(
                            "H1-H2", "TerminalActionPacket.handleItem", "creative_fill_packet", pre);
                }
                // #endregion
                BuildProject.find(projects, selectedId).ifPresent(p ->
                        BuildJobService.creativeFillMaterials(
                                player, p, BlockPos.ZERO, true, packet.hand));
            }
            case SET_MISSING_REPLACE -> {
                BuildProject.find(projects, selectedId).ifPresent(p ->
                        applyMissingReplace(player, p, packet.stringArg, false));
                ItemTerminalData.saveProjects(stack, projects, selectedId);
            }
            case CLEAR_MISSING_REPLACE -> {
                BuildProject.find(projects, selectedId).ifPresent(p ->
                        applyMissingReplaceClear(player, p, packet.stringArg, false));
                ItemTerminalData.saveProjects(stack, projects, selectedId);
            }
        }
        reopenItem(player, packet.hand);
    }

    private static boolean applyStartLayer(ServerPlayer player, BuildProject project, String raw) {
        if (project.status() == BuildProject.Status.BUILDING
                || project.status() == BuildProject.Status.PAUSED) {
            player.sendSystemMessage(Component.translatable(
                    "message.touhou_maid_construction.start_layer_locked"));
            return false;
        }
        int requested;
        try {
            requested = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        int height = 1;
        try {
            var summary = SchematicFiles.summarize(
                    SchematicFiles.resolveSchematic(project.schematicPath()));
            height = Math.max(1, summary.sizeY());
        } catch (Exception ignored) {
        }
        int clamped = com.touhoumaid.construction.build.StartLayerPolicy.clamp(requested, height);
        project.setStartLayer(clamped);
        return true;
    }

    private static void startBuild(ServerPlayer player, BuildProject p, ConstructionTerminalBE terminal) {
        if (!p.siteConfirmed() || p.anchor() == null) {
            player.sendSystemMessage(Component.translatable(
                    "message.touhou_maid_construction.need_site"));
            return;
        }
        if (p.hasConflicts()) {
            player.sendSystemMessage(Component.translatable(
                    "message.touhou_maid_construction.site_conflicts", p.conflictCount()));
            return;
        }
        if (BuildJobService.start(
                player,
                p,
                terminal.getBlockPos(),
                false,
                InteractionHand.MAIN_HAND,
                new BlockTerminalMaterialSource(terminal))) {
            terminal.setChanged();
        }
    }

    private static void pauseBuild(ServerPlayer player, BuildProject p, ConstructionTerminalBE terminal) {
        BuildJobKey key = p.buildJobKey().orElseGet(() -> new BuildJobKey(
                p.id(), terminal.getBlockPos(), false, InteractionHand.MAIN_HAND, player.getUUID()));
        BuildJobService.pause(player.serverLevel(), p, key);
        terminal.setChanged();
        player.sendSystemMessage(Component.translatable("message.touhou_maid_construction.build_paused"));
    }

    private static void finishBuild(ServerPlayer player, BuildProject p, ConstructionTerminalBE terminal) {
        BuildJobKey key = p.buildJobKey().orElseGet(() -> new BuildJobKey(
                p.id(), terminal.getBlockPos(), false, InteractionHand.MAIN_HAND, player.getUUID()));
        if (BuildJobService.forceFinish(player, p, key)) {
            terminal.setChanged();
        }
    }

    private static void toggleMaid(ServerPlayer player, BuildProject project, String maidIdStr) {
        try {
            UUID maidId = UUID.fromString(maidIdStr);
            project.toggleAssignedMaid(maidId);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private static void applyMissingReplace(
            ServerPlayer player, BuildProject project, String packed, boolean blockTerminal) {
        if (!MissingBlockReplacePolicy.canEditReplacements(project.status())) {
            player.sendSystemMessage(Component.translatable(
                    "message.touhou_maid_construction.missing_replace_locked"));
            return;
        }
        int sep = packed.indexOf('|');
        if (sep <= 0 || sep >= packed.length() - 1) {
            return;
        }
        String missingId = packed.substring(0, sep);
        String replacementId = packed.substring(sep + 1);
        ResourceLocation replacementRl = ResourceLocation.tryParse(replacementId);
        if (replacementRl == null || !BuiltInRegistries.BLOCK.containsKey(replacementRl)) {
            player.sendSystemMessage(Component.translatable(
                    "message.touhou_maid_construction.missing_replace_invalid"));
            return;
        }
        if (BuiltInRegistries.BLOCK.get(replacementRl).defaultBlockState().isAir()) {
            player.sendSystemMessage(Component.translatable(
                    "message.touhou_maid_construction.missing_replace_invalid"));
            return;
        }
        project.setMissingBlockReplacement(missingId, replacementId);
        if (blockTerminal) {
            // terminal.setChanged happens via reopen path callers
        }
        BuildJobService.rebuildPausedJobForReplacements(player.serverLevel(), project);
        player.sendSystemMessage(Component.translatable(
                "message.touhou_maid_construction.missing_replace_set", missingId, replacementId));
    }

    private static void applyMissingReplaceClear(
            ServerPlayer player, BuildProject project, String missingId, boolean blockTerminal) {
        if (!MissingBlockReplacePolicy.canEditReplacements(project.status())) {
            player.sendSystemMessage(Component.translatable(
                    "message.touhou_maid_construction.missing_replace_locked"));
            return;
        }
        project.clearMissingBlockReplacement(missingId);
        BuildJobService.rebuildPausedJobForReplacements(player.serverLevel(), project);
        player.sendSystemMessage(Component.translatable(
                "message.touhou_maid_construction.missing_replace_cleared", missingId));
    }

    private static void importSchematic(ServerPlayer player, ConstructionTerminalBE terminal, String fileName) {
        try {
            var path = SchematicFiles.resolveSchematic(fileName);
            if (!Files.isRegularFile(path)) {
                player.sendSystemMessage(Component.translatable(
                        "message.touhou_maid_construction.schematic_missing", fileName));
                return;
            }
            String name = fileName.endsWith(".litematic")
                    ? fileName.substring(0, fileName.length() - ".litematic".length())
                    : fileName;
            terminal.addProject(BuildProject.createNew(name, fileName));
            player.sendSystemMessage(Component.translatable(
                    "message.touhou_maid_construction.schematic_imported", fileName));
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Import failed: " + e.getMessage()));
        }
    }

    private static void importSchematicToList(ServerPlayer player, List<BuildProject> projects, String fileName) {
        try {
            var path = SchematicFiles.resolveSchematic(fileName);
            if (!Files.isRegularFile(path)) {
                player.sendSystemMessage(Component.translatable(
                        "message.touhou_maid_construction.schematic_missing", fileName));
                return;
            }
            String name = fileName.endsWith(".litematic")
                    ? fileName.substring(0, fileName.length() - ".litematic".length())
                    : fileName;
            projects.add(BuildProject.createNew(name, fileName));
            player.sendSystemMessage(Component.translatable(
                    "message.touhou_maid_construction.schematic_imported", fileName));
        } catch (Exception e) {
            player.sendSystemMessage(Component.literal("Import failed: " + e.getMessage()));
        }
    }

    public static void reopen(ServerPlayer player, ConstructionTerminalBE terminal) {
        try {
            SchematicFiles.ensureSchematicsDir();
        } catch (Exception ignored) {
        }
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                OpenTerminalHubPacket.forBlock(
                        terminal.getBlockPos(),
                        terminal.getProjects(),
                        terminal.getSelectedProjectId(),
                        SchematicFiles.listLitematicNames(),
                        VaultCounts.fromContainer(terminal),
                        BuildJobService.listNearbyMaids(player)));
        syncPreviewForPlayer(player, terminal.getSelectedProject().orElse(null));
    }

    public static void reopenItem(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof ConstructionTerminalItem)) {
            return;
        }
        try {
            SchematicFiles.ensureSchematicsDir();
        } catch (Exception ignored) {
        }
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                OpenTerminalHubPacket.forItem(
                        hand,
                        ItemTerminalData.getProjects(stack),
                        ItemTerminalData.getSelectedId(stack),
                        SchematicFiles.listLitematicNames(),
                        ItemTerminalData.vaultCounts(stack, player.getServer()),
                        BuildJobService.listNearbyMaids(player)));
        syncPreviewForPlayer(player, BuildProject.find(ItemTerminalData.getProjects(stack),
                ItemTerminalData.getSelectedId(stack)).orElse(null));
    }

    private static void syncPreviewForPlayer(ServerPlayer player, BuildProject project) {
        if (project != null && project.siteConfirmed() && project.anchor() != null) {
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new SitePreviewSyncPacket(
                            SitePreviewSyncPacket.Action.LOCK_ANCHORED,
                            project.anchor(),
                            project.schematicPath(),
                            project.coverTerrain(),
                            project.placementYaw(),
                            project.startLayer()));
        } else {
            ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new SitePreviewSyncPacket(
                            SitePreviewSyncPacket.Action.CLEAR,
                            null,
                            "",
                            false));
        }
    }

    public static void openItemHub(ServerPlayer player, InteractionHand hand) {
        reopenItem(player, hand);
    }
}
