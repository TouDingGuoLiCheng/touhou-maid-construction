package com.touhoumaid.construction.client;

import com.touhoumaid.construction.api.client.HubPage2ButtonRegistry;
import com.touhoumaid.construction.api.client.HubTerminalRef;
import com.touhoumaid.construction.network.ModNetwork;
import com.touhoumaid.construction.network.OpenTerminalHubPacket;
import com.touhoumaid.construction.network.BuildProgressPacket;
import com.touhoumaid.construction.network.TerminalActionPacket;
import com.touhoumaid.construction.build.BuildJobService;
import com.touhoumaid.construction.project.BuildDifficulty;
import com.touhoumaid.construction.project.BuildProject;
import com.touhoumaid.construction.project.MaterialBlockPolicy;
import com.touhoumaid.construction.project.MaterialDisplay;
import com.touhoumaid.construction.project.MissingBlockReplacePolicy;
import com.touhoumaid.construction.project.SchematicFiles;
import com.touhoumaid.construction.project.VaultCounts;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Three-page construction hub: warm wood desk theme, ~70% of screen.
 */
@OnlyIn(Dist.CLIENT)
public class ConstructionHubScreen extends Screen {
    private static final ResourceLocation BG_TEX =
            new ResourceLocation("touhou_maid_construction", "textures/gui/hub_desk.png");

    private static final int SIDE_EDGE = 0xFFC4783A;
    private static final int PARCHMENT = 0xB8F3E6D0;
    private static final int PARCHMENT_DARK = 0xA8E8D5B8;
    private static final int ACCENT = 0xFFC4783A;
    private static final int TEXT = 0xFF3B2A1E;
    private static final int MUTED = 0xFF7A5C45;
    private static final int DANGER = 0xFFB54A3A;
    private static final int OK_GREEN = 0xFF3D8B5A;

    private final BlockPos pos;
    private final boolean itemMode;
    private final InteractionHand hand;
    private List<BuildProject> projects;
    private String selectedId;
    private Map<String, Integer> vaultCounts;
    private List<BuildJobService.MaidInfo> nearbyMaids = List.of();
    private int page = 1;
    private int leftScroll;
    private int materialScroll;
    private int maidScroll;
    private SchematicFiles.MaterialSummary summary;
    private SchematicFiles.MaterialSummary rawSummary;
    private String statusLine = "";
    private final List<MaterialCellHit> materialCellHits = new ArrayList<>();
    private final List<MissingReplaceRowHit> missingReplaceHits = new ArrayList<>();
    private int missingReplaceScroll;

    private final List<MaidRowHit> maidRowHits = new ArrayList<>();

    private int guiW;
    private int guiH;
    private int sideW;
    private int left;
    private int top;
    private int dropX;
    private int dropY;
    private int dropW;
    private int dropH;
    private int materialPanelTop;
    private int materialPanelBottom;
    private int materialPanelW;
    private static final int PAGE1_BUTTON_H = 20;
    private static final int PAGE1_FOOTER_GAP = 6;
    private static final int LINE_H = 11;
    private static final int MATERIAL_ROW_H = 18;
    private static final int MATERIAL_FOOTER_H = 28;
    private static final int MATERIAL_COLS = 7;
    private static final int BTN_H = 18;
    private static final int BTN_GAP = 4;
    private static final int BTN_W_BACK = 44;
    private static final int BTN_W = 56;
    private static final int MAID_ROW_H = 24;
    private static final int PROGRESS_BLOCK_H = 62;
    private static final int PROGRESS_BAR_H = 8;

    private int maidPanelTop;
    private int maidPanelBottom;
    private int maidPanelW;

    private int page1ButtonTop() {
        return top + guiH - PAGE1_BUTTON_H - PAGE1_FOOTER_GAP;
    }

    private int page1OverviewBottom() {
        return selected().isPresent() ? page1ButtonTop() - 4 : top + guiH - 8;
    }

    private boolean fitsLine(int y, int bottom) {
        return y + LINE_H <= bottom;
    }

    private int drawLine(GuiGraphics g, int contentX, int y, int bottom, String text, int color) {
        if (!fitsLine(y, bottom)) {
            return y;
        }
        g.drawString(font, text, contentX + 4, y, color, false);
        return y + LINE_H;
    }

    public ConstructionHubScreen(OpenTerminalHubPacket packet) {
        super(Component.translatable("gui.touhou_maid_construction.hub"));
        this.pos = packet.pos();
        this.itemMode = packet.itemMode();
        this.hand = packet.hand();
        this.projects = new ArrayList<>(packet.projects());
        this.selectedId = packet.selectedId();
        this.vaultCounts = new HashMap<>(packet.vaultCounts());
        this.nearbyMaids = new ArrayList<>(packet.nearbyMaids());
        restoreSessionUiState();
    }

    public void applyBuildProgress(BuildProgressPacket packet) {
        for (BuildProject project : projects) {
            if (project.id().equals(packet.key().projectId())) {
                project.setPlacedBlocks(packet.placedCount());
                project.setTotalBlocks(packet.totalBlocks());
                project.setConsumedMaterials(packet.consumed());
                project.setPlacedByBlockId(packet.placedByBlockId());
                if (packet.done()) {
                    project.setStatus(BuildProject.Status.DONE);
                    if (!packet.placementFailures().isEmpty()) {
                        project.setPlacementFailures(packet.placementFailures());
                    }
                } else if (packet.paused()) {
                    project.setStatus(BuildProject.Status.PAUSED);
                } else {
                    project.setStatus(BuildProject.Status.BUILDING);
                }
                break;
            }
        }
        applyMaidMaterialWaits(packet.maidMaterialWaits());
    }

    private void applyMaidMaterialWaits(Map<UUID, String> waits) {
        if (waits == null || waits.isEmpty()) {
            nearbyMaids = nearbyMaids.stream()
                    .map(m -> new BuildJobService.MaidInfo(m.id(), m.name(), m.onConstructionTask(), ""))
                    .toList();
            return;
        }
        List<BuildJobService.MaidInfo> updated = new ArrayList<>(nearbyMaids.size());
        for (BuildJobService.MaidInfo maid : nearbyMaids) {
            String wait = waits.getOrDefault(maid.id(), "");
            updated.add(new BuildJobService.MaidInfo(
                    maid.id(), maid.name(), maid.onConstructionTask(), wait));
        }
        nearbyMaids = updated;
    }

    public void applyPacket(OpenTerminalHubPacket packet) {
        boolean selectionChanged = !packet.selectedId().equals(this.selectedId);
        if (selectionChanged) {
            cacheUiStateForSelected();
        }
        this.projects = new ArrayList<>(packet.projects());
        this.selectedId = packet.selectedId();
        this.vaultCounts = new HashMap<>(packet.vaultCounts());
        this.nearbyMaids = new ArrayList<>(packet.nearbyMaids());
        if (selectionChanged) {
            restoreUiStateForSelected();
        } else {
            reloadSummary();
            rebuildHubButtons();
        }
    }

    private void restoreSessionUiState() {
        if (selectedId == null || selectedId.isEmpty()) {
            return;
        }
        page = HubSessionCache.page(selectedId);
        materialScroll = HubSessionCache.materialScroll(selectedId);
        maidScroll = HubSessionCache.maidScroll(selectedId);
    }

    private void cacheUiStateForSelected() {
        if (selectedId == null || selectedId.isEmpty()) {
            return;
        }
        HubSessionCache.setPage(selectedId, page);
        HubSessionCache.setMaterialScroll(selectedId, materialScroll);
        HubSessionCache.setMaidScroll(selectedId, maidScroll);
    }

    private void restoreUiStateForSelected() {
        restoreSessionUiState();
        reloadSummary();
        rebuildHubButtons();
    }

    private void setPage(int newPage) {
        page = newPage;
        cacheUiStateForSelected();
        rebuildHubButtons();
    }

    private void selectProject(String projectId) {
        if (projectId.equals(selectedId)) {
            return;
        }
        cacheUiStateForSelected();
        selectedId = projectId;
        restoreUiStateForSelected();
    }

    private void sendSelect(String projectId) {
        ModNetwork.CHANNEL.sendToServer(itemMode
                ? TerminalActionPacket.selectItem(hand, projectId)
                : TerminalActionPacket.select(pos, projectId));
    }

    private void sendImport(String fileName) {
        ModNetwork.CHANNEL.sendToServer(itemMode
                ? TerminalActionPacket.importSchematicItem(hand, fileName)
                : TerminalActionPacket.importSchematic(pos, fileName));
    }

    private void sendDelete(String projectId) {
        ModNetwork.CHANNEL.sendToServer(itemMode
                ? TerminalActionPacket.deleteItem(hand, projectId)
                : TerminalActionPacket.delete(pos, projectId));
    }

    private void sendCover(boolean enabled) {
        ModNetwork.CHANNEL.sendToServer(itemMode
                ? TerminalActionPacket.coverItem(hand, enabled)
                : TerminalActionPacket.cover(pos, enabled));
    }

    private void sendIgnoreOverlaps(boolean enabled) {
        ModNetwork.CHANNEL.sendToServer(itemMode
                ? TerminalActionPacket.ignoreOverlapsItem(hand, enabled)
                : TerminalActionPacket.ignoreOverlaps(pos, enabled));
    }

    private void sendStartLayer(int layer) {
        ModNetwork.CHANNEL.sendToServer(itemMode
                ? TerminalActionPacket.startLayerItem(hand, layer)
                : TerminalActionPacket.startLayer(pos, layer));
        selected().ifPresent(p -> {
            int height = summary == null ? Math.max(1, layer + 1) : Math.max(1, summary.sizeY());
            int clamped = com.touhoumaid.construction.build.StartLayerPolicy.clamp(layer, height);
            p.setStartLayer(clamped);
            SitePickController.setStartLayer(clamped);
        });
        reloadSummary();
        rebuildHubButtons();
    }

    private void sendAutoCraft(boolean enabled) {
        ModNetwork.CHANNEL.sendToServer(itemMode
                ? TerminalActionPacket.autoCraftItem(hand, enabled)
                : TerminalActionPacket.autoCraft(pos, enabled));
        selected().ifPresent(p -> p.setAutoCraftMaterials(enabled));
        rebuildHubButtons();
    }

    private void sendToggleMaid(UUID maidId) {
        ModNetwork.CHANNEL.sendToServer(itemMode
                ? TerminalActionPacket.toggleMaidItem(hand, maidId.toString())
                : TerminalActionPacket.toggleMaid(pos, maidId.toString()));
    }

    private void sendEnterSitePick() {
        ModNetwork.CHANNEL.sendToServer(itemMode
                ? TerminalActionPacket.enterSitePickItem(hand)
                : TerminalActionPacket.enterSitePick(pos));
    }

    private void sendOpenMaterials() {
        ModNetwork.CHANNEL.sendToServer(itemMode
                ? TerminalActionPacket.openMaterialsItem(hand)
                : TerminalActionPacket.openMaterials(pos));
    }

    private void sendStart() {
        ModNetwork.CHANNEL.sendToServer(itemMode
                ? TerminalActionPacket.startItem(hand)
                : TerminalActionPacket.start(pos));
    }

    private void sendPause() {
        ModNetwork.CHANNEL.sendToServer(itemMode
                ? TerminalActionPacket.pauseItem(hand)
                : TerminalActionPacket.pause(pos));
    }

    private void sendFinish() {
        ModNetwork.CHANNEL.sendToServer(itemMode
                ? TerminalActionPacket.finishItem(hand)
                : TerminalActionPacket.finish(pos));
        selected().ifPresent(p -> {
            if (p.status() == BuildProject.Status.BUILDING || p.status() == BuildProject.Status.PAUSED) {
                p.setStatus(BuildProject.Status.DONE);
            }
        });
        rebuildHubButtons();
    }

    private void sendRefresh() {
        ModNetwork.CHANNEL.sendToServer(itemMode
                ? TerminalActionPacket.refreshItem(hand)
                : TerminalActionPacket.refresh(pos));
    }

    private void sendIgnoreMaterial(String blockId) {
        ModNetwork.CHANNEL.sendToServer(itemMode
                ? TerminalActionPacket.ignoreMaterialItem(hand, blockId)
                : TerminalActionPacket.ignoreMaterial(pos, blockId));
    }

    private void sendRestoreMaterial(String blockId) {
        ModNetwork.CHANNEL.sendToServer(itemMode
                ? TerminalActionPacket.restoreMaterialItem(hand, blockId)
                : TerminalActionPacket.restoreMaterial(pos, blockId));
    }

    private void sendCreativeFill() {
        ModNetwork.CHANNEL.sendToServer(itemMode
                ? TerminalActionPacket.creativeFillItem(hand)
                : TerminalActionPacket.creativeFill(pos));
    }

    @Override
    protected void init() {
        guiW = Math.max(320, (int) (width * 0.70F));
        guiH = Math.max(200, (int) (height * 0.70F));
        sideW = Math.max(100, guiW / 4);
        left = (width - guiW) / 2;
        top = (height - guiH) / 2;
        dropX = left + sideW + 16;
        dropY = top + 32;
        dropW = guiW - sideW - 32;
        dropH = 72;
        reloadSummary();
        rebuildHubButtons();
    }

    @Override
    public void onClose() {
        cacheUiStateForSelected();
        super.onClose();
    }

    private Button addWoodButton(Component label, Button.OnPress onPress, int x, int y, int w) {
        return addRenderableWidget(HubWoodButton.create(x, y, w, BTN_H, label, onPress));
    }

    private void rebuildHubButtons() {
        clearWidgets();
        int contentX = left + sideW + 8;
        int contentY = top + 28;

        if (page == 1) {
            if (selected().isPresent()) {
                addWoodButton(Component.translatable("gui.touhou_maid_construction.open_project"),
                        b -> setPage(2), contentX, page1ButtonTop(), 88);
                addWoodButton(Component.translatable("gui.touhou_maid_construction.delete_project"),
                        b -> selected().ifPresent(p -> sendDelete(p.id())),
                        contentX + 88 + BTN_GAP, page1ButtonTop(), 72);
            }
        } else if (page == 2) {
            int row1 = contentY;
            int row2 = contentY + BTN_H + BTN_GAP;
            addWoodButton(Component.translatable("gui.touhou_maid_construction.back"),
                    b -> setPage(1), contentX, row1, BTN_W_BACK);
            addWoodButton(Component.translatable("gui.touhou_maid_construction.materials_vault"),
                    b -> sendOpenMaterials(), contentX + BTN_W_BACK + BTN_GAP, row1, BTN_W);
            int row1NextX = contentX + BTN_W_BACK + BTN_GAP + BTN_W + BTN_GAP;
            HubTerminalRef terminalRef = new HubTerminalRef(pos, itemMode, hand);
            String selectedProjectId = selectedId == null ? "" : selectedId;
            for (HubPage2ButtonRegistry.Entry extra : HubPage2ButtonRegistry.entries()) {
                final HubPage2ButtonRegistry.Entry entry = extra;
                int extraW = BTN_W + 8;
                addWoodButton(Component.translatable(entry.labelKey()),
                        b -> entry.onClick().accept(terminalRef, selectedProjectId),
                        row1NextX, row1, extraW);
                row1NextX += extraW + BTN_GAP;
            }
            BuildProject project = selected().orElse(null);
            if (project != null) {
                boolean cover = project.coverTerrain();
                addWoodButton(
                        Component.translatable(cover
                                ? "gui.touhou_maid_construction.cover_on"
                                : "gui.touhou_maid_construction.cover_off"),
                        b -> sendCover(!cover),
                        row1NextX, row1, BTN_W + 8);
                boolean ignore = project.ignoreOverlaps();
                int row3 = contentY + (BTN_H + BTN_GAP) * 2;
                // Row 3: ignore + auto-craft + start-layer (- / N / +), kept narrow so text below stays clear.
                int toggleW = 86;
                int stepW = 16;
                int layerLabelW = 78;
                addWoodButton(
                        Component.translatable(ignore
                                ? "gui.touhou_maid_construction.ignore_overlaps_on"
                                : "gui.touhou_maid_construction.ignore_overlaps_off"),
                        b -> sendIgnoreOverlaps(!ignore),
                        contentX, row3, toggleW);
                boolean autoCraft = project.autoCraftMaterials();
                int autoX = contentX + toggleW + BTN_GAP;
                addWoodButton(
                        Component.translatable(autoCraft
                                ? "gui.touhou_maid_construction.auto_craft_on"
                                : "gui.touhou_maid_construction.auto_craft_off"),
                        b -> sendAutoCraft(!autoCraft),
                        autoX, row3, toggleW);
                int height = summary == null ? 1 : Math.max(1, summary.sizeY());
                int startLayer = com.touhoumaid.construction.build.StartLayerPolicy.clamp(
                        project.startLayer(), height);
                boolean layerLocked = project.status() == BuildProject.Status.BUILDING
                        || project.status() == BuildProject.Status.PAUSED;
                int layerX = autoX + toggleW + BTN_GAP;
                Button minus = addWoodButton(Component.literal("-"),
                        b -> sendStartLayer(startLayer - 1),
                        layerX, row3, stepW);
                minus.active = !layerLocked && startLayer > 0;
                addWoodButton(
                        Component.translatable(
                                "gui.touhou_maid_construction.start_layer", startLayer, height),
                        b -> {
                        },
                        layerX + stepW + BTN_GAP, row3, layerLabelW);
                Button plus = addWoodButton(Component.literal("+"),
                        b -> sendStartLayer(startLayer + 1),
                        layerX + stepW + BTN_GAP + layerLabelW + BTN_GAP, row3, stepW);
                plus.active = !layerLocked && startLayer < height - 1;
                addWoodButton(Component.translatable("gui.touhou_maid_construction.pick_site"),
                        b -> sendEnterSitePick(), contentX, row2, BTN_W);
                addWoodButton(
                        Component.translatable(SitePickController.previewVisible()
                                ? "gui.touhou_maid_construction.preview_on"
                                : "gui.touhou_maid_construction.preview_off"),
                        b -> {
                            SitePickController.togglePreviewVisible();
                            rebuildHubButtons();
                        },
                        contentX + BTN_W + BTN_GAP, row2, BTN_W);
                int row2NextX = contentX + (BTN_W + BTN_GAP) * 2;
                if (project.siteConfirmed() && !project.hasConflicts()) {
                    addWoodButton(Component.translatable("gui.touhou_maid_construction.to_build"),
                            b -> setPage(3),
                            row2NextX, row2, BTN_W);
                    row2NextX += BTN_W + BTN_GAP;
                }
                if (minecraft.player != null && minecraft.player.isCreative()) {
                    addWoodButton(Component.translatable("gui.touhou_maid_construction.creative_fill"),
                            b -> sendCreativeFill(),
                            row2NextX, row2, BTN_W + 8);
                    row2NextX += BTN_W + 8 + BTN_GAP;
                }
                addWoodButton(Component.translatable("gui.touhou_maid_construction.missing_replace"),
                        b -> setPage(4),
                        row2NextX, row2, BTN_W + 24);
            }
        } else if (page == 4) {
            addWoodButton(Component.translatable("gui.touhou_maid_construction.back"),
                    b -> setPage(2), contentX, contentY, BTN_W_BACK);
        } else {
            addWoodButton(Component.translatable("gui.touhou_maid_construction.back"),
                    b -> setPage(2), contentX, contentY, BTN_W_BACK);
            HubWoodButton startBtn = HubWoodButton.create(
                    contentX + BTN_W_BACK + BTN_GAP,
                    contentY,
                    BTN_W,
                    BTN_H,
                    Component.translatable("gui.touhou_maid_construction.start"),
                    b -> sendStart());
            startBtn.active = selected().map(p -> !p.assignedMaids().isEmpty()).orElse(false);
            addRenderableWidget(startBtn);
            addWoodButton(Component.translatable("gui.touhou_maid_construction.pause"),
                    b -> sendPause(), contentX + BTN_W_BACK + BTN_GAP + BTN_W + BTN_GAP, contentY, BTN_W);
            HubWoodButton finishBtn = HubWoodButton.create(
                    contentX + BTN_W_BACK + BTN_GAP + (BTN_W + BTN_GAP) * 2,
                    contentY,
                    BTN_W,
                    BTN_H,
                    Component.translatable("gui.touhou_maid_construction.finish"),
                    b -> sendFinish());
            // Always pressable once a project is selected (any status).
            finishBtn.active = selected().isPresent();
            addRenderableWidget(finishBtn);
            addWoodButton(
                    Component.translatable(SitePickController.previewVisible()
                            ? "gui.touhou_maid_construction.preview_on"
                            : "gui.touhou_maid_construction.preview_off"),
                    b -> {
                        SitePickController.togglePreviewVisible();
                        rebuildHubButtons();
                    },
                    contentX + BTN_W_BACK + BTN_GAP + (BTN_W + BTN_GAP) * 3, contentY, BTN_W + 8);
        }
    }

    private void reloadSummary() {
        summary = null;
        rawSummary = null;
        Optional<BuildProject> project = selected();
        if (project.isEmpty() || project.get().schematicPath().isEmpty()) {
            return;
        }
        try {
            Path path = SchematicFiles.resolveSchematic(project.get().schematicPath());
            rawSummary = SchematicFiles.summarize(path, project.get().startLayer());
            summary = SchematicFiles.applyReplacements(
                    rawSummary, project.get().missingBlockReplacements());
            expireStaleMissingHighlight();
        } catch (Exception e) {
            statusLine = Component.translatable(
                    "gui.touhou_maid_construction.summary_load_failed", e.getMessage()).getString();
        }
    }

    /** A missing-block highlight only lives while this project still lists that id. */
    private void expireStaleMissingHighlight() {
        String armed = SitePickController.highlightMissingBlockId();
        if (armed.isEmpty() || rawSummary == null) {
            return;
        }
        if (!rawSummary.missingBlocks().contains(armed)) {
            SitePickController.clearHighlightMissingBlock();
        }
    }

    private Optional<BuildProject> selected() {
        return BuildProject.find(projects, selectedId);
    }

    private static int requiredBlockCount(BuildProject project, SchematicFiles.MaterialLine line) {
        return project.requiredMaterialCount(line.blockId(), line.count());
    }

    private int materialHeaderBlockCount(BuildProject project) {
        if (summary == null) {
            return 0;
        }
        if (!project.tracksBuildProgress()) {
            return summary.totalBlocks();
        }
        int total = project.totalBlocks() > 0 ? project.totalBlocks() : summary.totalBlocks();
        return Math.max(0, total - project.placedBlocks());
    }

    private void importFromPicker() {
        SchematicImportClient.pickFileOrFolder(names -> {
            for (String name : names) {
                sendImport(name);
            }
        }, this::sendRefresh);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.setColor(1F, 1F, 1F, 1F);
        g.blit(BG_TEX, left, top, 0, 0, guiW, guiH, 400, 280);
        g.fill(left, top, left + guiW, top + guiH, 0x22000000);

        fillParchment(g, left + 4, top + 4, left + sideW - 4, top + guiH - 4);
        fillParchment(g, left + sideW + 4, top + 4, left + guiW - 4, top + guiH - 4);
        g.fill(left + sideW - 1, top + 8, left + sideW, top + guiH - 8, SIDE_EDGE);

        g.drawString(font, title.getString(), left + 10, top + 10, TEXT, false);
        g.drawString(font, Component.translatable("gui.touhou_maid_construction.page", page).getString(),
                left + sideW + 10, top + 10, MUTED, false);

        renderSidebar(g);
        int contentX = left + sideW + 8;
        if (page == 1) {
            boolean hoverDrop = mouseX >= dropX && mouseX <= dropX + dropW
                    && mouseY >= dropY && mouseY <= dropY + dropH;
            g.fill(dropX, dropY, dropX + dropW, dropY + dropH, hoverDrop ? 0x66C4783A : 0x44A67C52);
            g.fill(dropX + 1, dropY + 1, dropX + dropW - 1, dropY + dropH - 1, 0x22FFFFFF);
            g.drawCenteredString(font, "[+]", dropX + dropW / 2, dropY + dropH / 2 - 12, TEXT);
            g.drawCenteredString(font,
                    Component.translatable("gui.touhou_maid_construction.drop_hint").getString(),
                    dropX + dropW / 2, dropY + dropH / 2 + 6, MUTED);
            renderPage1Overview(g, contentX);
        } else if (page == 2) {
            renderPage2(g, contentX);
        } else if (page == 4) {
            renderPage4(g, contentX);
        } else {
            renderPage3(g, contentX);
        }
        if (!statusLine.isEmpty() && page != 1) {
            g.drawString(font, trim(statusLine, 56), contentX, top + guiH - 12, ACCENT, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (page == 2) {
            for (MaterialCellHit hit : materialCellHits) {
                if (!hit.contains(mouseX, mouseY)) {
                    continue;
                }
                int ax0 = hit.actionX0();
                int ay0 = hit.actionY0();
                int ax1 = hit.actionX1();
                int ay1 = hit.actionY1();
                g.fill(ax0, ay0, ax1, ay1, hit.ignored() ? 0xCC3D8B5A : 0xCCB54A3A);
                g.drawString(font, hit.ignored() ? "+" : "x", ax0 + 2, ay0 + 1, 0xFFFFF6EA, false);
                int have = VaultCounts.haveForBlockId(vaultCounts, hit.line().blockId());
                BuildProject project = selected().orElse(null);
                int required = project == null ? hit.line().count() : requiredBlockCount(project, hit.line());
                List<Component> tips = new ArrayList<>();
                tips.add(Component.literal(hit.line().displayName()));
                if (hit.ignored()) {
                    tips.add(Component.translatable("gui.touhou_maid_construction.material_ignored_hint"));
                } else {
                    tips.add(Component.literal(have + " / " + required));
                    tips.add(Component.translatable("gui.touhou_maid_construction.material_ignore_hint"));
                }
                g.renderComponentTooltip(font, tips, mouseX, mouseY);
                break;
            }
        }
    }

    private void fillParchment(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.fill(x1, y1, x2, y2, PARCHMENT);
        g.fill(x1, y1, x2, y1 + 1, PARCHMENT_DARK);
        g.fill(x1, y2 - 1, x2, y2, PARCHMENT_DARK);
    }

    private void renderSidebar(GuiGraphics g) {
        g.drawString(font, Component.translatable("gui.touhou_maid_construction.projects").getString(),
                left + 10, top + 28, MUTED, false);
        int y = top + 44;
        int shown = 0;
        int maxRows = Math.max(5, (guiH - 60) / 20);
        for (int i = leftScroll; i < projects.size() && shown < maxRows; i++, shown++) {
            BuildProject project = projects.get(i);
            boolean sel = project.id().equals(selectedId);
            int x1 = left + 8;
            int y1 = y;
            int x2 = left + sideW - 8;
            int y2 = y + 18;
            g.fill(x1, y1, x2, y2, sel ? ACCENT : PARCHMENT_DARK);
            g.drawString(font, trim(project.name(), 14), x1 + 4, y1 + 5, sel ? 0xFFFFF6EA : TEXT, false);
            y += 20;
        }
    }

    private void renderPage1Overview(GuiGraphics g, int contentX) {
        int overviewTop = dropY + dropH + 6;
        int overviewBottom = page1OverviewBottom();
        if (overviewBottom <= overviewTop + LINE_H) {
            return;
        }
        g.fill(contentX, overviewTop, left + guiW - 8, overviewBottom, PARCHMENT_DARK);

        int y = overviewTop + 4;
        y = drawLine(g, contentX, y, overviewBottom,
                Component.translatable("gui.touhou_maid_construction.overview_title").getString(), ACCENT);

        Optional<BuildProject> project = selected();
        if (project.isEmpty()) {
            drawLine(g, contentX, y, overviewBottom,
                    Component.translatable("gui.touhou_maid_construction.pick_first").getString(), MUTED);
            return;
        }

        BuildProject p = project.get();
        String statusKey = "gui.touhou_maid_construction.status." + p.status().name().toLowerCase();
        y = drawLine(g, contentX, y, overviewBottom,
                Component.translatable("gui.touhou_maid_construction.current_project", p.name()).getString(), TEXT);
        y = drawLine(g, contentX, y, overviewBottom,
                "[" + Component.translatable(statusKey).getString() + "]", MUTED);

        if (summary == null) {
            drawLine(g, contentX, y, overviewBottom,
                    Component.translatable("gui.touhou_maid_construction.loading_summary").getString(), MUTED);
            return;
        }

        BuildDifficulty difficulty = BuildDifficulty.fromBlockCount(summary.totalBlocks());
        y = drawLine(g, contentX, y, overviewBottom,
                Component.translatable("gui.touhou_maid_construction.overview_footprint",
                        summary.sizeX(), summary.sizeY(), summary.sizeZ(),
                        summary.sizeX() * summary.sizeZ()).getString(), TEXT);
        y = drawLine(g, contentX, y, overviewBottom,
                Component.translatable("gui.touhou_maid_construction.overview_stats",
                        summary.totalBlocks(), difficulty.label(), summary.lines().size()).getString(), TEXT);

        String siteText;
        if (p.siteConfirmed() && p.anchor() != null) {
            siteText = p.anchor().getX() + ", " + p.anchor().getY() + ", " + p.anchor().getZ();
        } else if (p.hasConflicts()) {
            siteText = Component.translatable("gui.touhou_maid_construction.site_conflict_hint",
                    p.conflictCount()).getString();
        } else {
            siteText = Component.translatable("gui.touhou_maid_construction.overview_site_pending").getString();
        }
        drawLine(g, contentX, y, overviewBottom,
                Component.translatable("gui.touhou_maid_construction.site", siteText).getString(), MUTED);
    }

    private void renderPage2(GuiGraphics g, int contentX) {
        materialCellHits.clear();
        BuildProject project = selected().orElse(null);
        if (project == null) {
            g.drawString(font, Component.translatable("gui.touhou_maid_construction.pick_first").getString(),
                    contentX, top + 80, TEXT, false);
            return;
        }

        materialPanelTop = top + 92;
        materialPanelBottom = top + guiH - 4;
        materialPanelW = left + guiW - 8 - contentX;
        g.fill(contentX, materialPanelTop, left + guiW - 8, materialPanelBottom, PARCHMENT_DARK);

        int y = materialPanelTop + 4;
        String statusKey = "gui.touhou_maid_construction.status." + project.status().name().toLowerCase();
        g.drawString(font, project.name() + "  [" + Component.translatable(statusKey).getString() + "]",
                contentX + 4, y, TEXT, false);
        y += LINE_H;

        String siteText;
        if (project.siteConfirmed() && project.anchor() != null) {
            siteText = project.anchor().getX() + "," + project.anchor().getY() + "," + project.anchor().getZ();
        } else if (project.hasConflicts()) {
            siteText = Component.translatable("gui.touhou_maid_construction.site_conflict_hint",
                    project.conflictCount()).getString();
        } else {
            siteText = "...";
        }
        g.drawString(font, Component.translatable("gui.touhou_maid_construction.site", siteText).getString(),
                contentX + 4, y, MUTED, false);
        y += LINE_H;

        if (summary == null) {
            g.drawString(font, Component.translatable("gui.touhou_maid_construction.no_summary").getString(),
                    contentX + 4, y, MUTED, false);
            return;
        }

        BuildDifficulty difficulty = BuildDifficulty.fromBlockCount(summary.totalBlocks());
        int headerBlocks = materialHeaderBlockCount(project);
        g.drawString(font, Component.translatable("gui.touhou_maid_construction.materials_header",
                        headerBlocks, summary.sizeX(), summary.sizeY(), summary.sizeZ())
                .getString() + "  ·  " + difficulty.label().getString(),
                contentX + 4, y, TEXT, false);
        y += LINE_H + 2;

        int cols = MATERIAL_COLS;
        int colW = materialPanelW / cols;
        int listBottom = materialPanelBottom - MATERIAL_FOOTER_H;
        int maxRows = Math.max(1, (listBottom - y) / MATERIAL_ROW_H);
        int slots = maxRows * cols;
        int shown = 0;
        for (int i = materialScroll; i < summary.lines().size() && shown < slots; i++) {
            SchematicFiles.MaterialLine line = summary.lines().get(i);
            if (MaterialBlockPolicy.isAutoFree(line.blockId())) {
                continue;
            }
            int slot = shown;
            int col = slot % cols;
            int row = slot / cols;
            int cellX = contentX + 4 + col * colW;
            int cellY = y + row * MATERIAL_ROW_H;
            if (cellY + MATERIAL_ROW_H > listBottom) {
                break;
            }
            int have = VaultCounts.haveForBlockId(vaultCounts, line.blockId());
            boolean ignored = project.isIgnoredBlock(line.blockId());
            int required = requiredBlockCount(project, line);
            boolean enough = ignored || have >= required;
            int qtyColor = ignored ? MUTED : (enough ? OK_GREEN : DANGER);
            ItemStack icon = line.iconStack();
            if (!icon.isEmpty()) {
                g.renderItem(icon, cellX, cellY);
                if (ignored) {
                    g.fill(cellX, cellY, cellX + 16, cellY + 16, 0x88000000);
                }
            }
            g.drawString(font, ignored ? "-" : String.valueOf(required), cellX + 18, cellY + 5, qtyColor, false);
            int actionX1 = cellX + colW - 4;
            int actionX0 = actionX1 - 10;
            int actionY0 = cellY;
            int actionY1 = cellY + 10;
            materialCellHits.add(new MaterialCellHit(
                    line, cellX, cellY, cellX + colW - 2, cellY + MATERIAL_ROW_H,
                    actionX0, actionY0, actionX1, actionY1, ignored));
            shown++;
        }

        int footerY = materialPanelBottom - MATERIAL_FOOTER_H + 1;
        List<String> placementFailures = project.placementFailures();
        if (!placementFailures.isEmpty()) {
            String failureText = String.join(", ", placementFailures);
            g.drawString(font, truncateForLine(
                    Component.translatable("gui.touhou_maid_construction.placement_fail_report", failureText)
                            .getString(), materialPanelW - 8), contentX + 4, footerY, DANGER, false);
            g.drawString(font, truncateForLine(
                    Component.translatable("gui.touhou_maid_construction.placement_fail_hint").getString(),
                    materialPanelW - 8), contentX + 4, footerY + LINE_H, MUTED, false);
        } else if (!summary.missingBlocks().isEmpty() || !summary.missingEntities().isEmpty()) {
            String sample = MaterialDisplay.summarizeMissingSample(
                    summary.missingBlocks(), summary.missingEntities(), 3);
            g.drawString(font, truncateForLine(Component.translatable(
                            "gui.touhou_maid_construction.missing_report_detail",
                            summary.missingBlocks().size(),
                            summary.missingEntities().size(),
                            sample).getString(), materialPanelW - 8),
                    contentX + 4, footerY, DANGER, false);
        } else {
            g.drawString(font, truncateForLine(
                    Component.translatable("gui.touhou_maid_construction.report_ok").getString(),
                    materialPanelW - 8), contentX + 4, footerY, ACCENT, false);
        }
        g.drawString(font, truncateForLine(
                Component.translatable("gui.touhou_maid_construction.known_limits").getString(),
                materialPanelW - 8), contentX + 4, footerY + LINE_H, MUTED, false);
    }

    private void renderPage4(GuiGraphics g, int contentX) {
        missingReplaceHits.clear();
        BuildProject project = selected().orElse(null);
        if (project == null) {
            g.drawString(font, Component.translatable("gui.touhou_maid_construction.pick_first").getString(),
                    contentX, top + 80, TEXT, false);
            return;
        }
        int panelTop = top + 52;
        int panelBottom = top + guiH - 4;
        int panelW = left + guiW - 8 - contentX;
        g.drawString(font, Component.translatable("gui.touhou_maid_construction.missing_replace_title").getString(),
                contentX, panelTop - 14, TEXT, false);
        g.fill(contentX, panelTop, left + guiW - 8, panelBottom, PARCHMENT_DARK);

        if (rawSummary == null || rawSummary.missingBlocks().isEmpty()) {
            g.drawString(font, Component.translatable("gui.touhou_maid_construction.no_missing").getString(),
                    contentX + 4, panelTop + 8, MUTED, false);
            return;
        }

        boolean canEdit = MissingBlockReplacePolicy.canEditReplacements(project.status());
        int rowH = 22;
        int y = panelTop + 4;
        int shown = 0;
        int maxRows = Math.max(1, (panelBottom - panelTop - 8) / rowH);
        List<String> missing = rawSummary.missingBlocks();
        for (int i = missingReplaceScroll; i < missing.size() && shown < maxRows; i++) {
            String missingId = missing.get(i);
            Optional<String> replacement = project.replacementForMissing(missingId);
            int rowY = y + shown * rowH;
            String label = truncateForLine(missingId, panelW - 210);
            g.drawString(font, label, contentX + 4, rowY + 6,
                    replacement.isPresent() ? OK_GREEN : DANGER, false);
            if (replacement.isPresent()) {
                String replName = truncateForLine(
                        MaterialDisplay.nameForBlockId(replacement.get()).getString(), 48);
                g.drawString(font, "→ " + replName, contentX + 4 + font.width(label) + 6, rowY + 6, MUTED, false);
            }

            int btnW = 52;
            int showX1 = left + guiW - 12;
            int showX0 = showX1 - btnW;
            int replaceX1 = showX0 - 4;
            int replaceX0 = replaceX1 - btnW;
            int clearX1 = replaceX0 - 4;
            int clearX0 = clearX1 - (replacement.isPresent() ? 40 : 0);

            g.fill(showX0, rowY + 2, showX1, rowY + 18, 0xCC8B5A3A);
            g.drawCenteredString(font,
                    Component.translatable("gui.touhou_maid_construction.missing_show").getString(),
                    (showX0 + showX1) / 2, rowY + 6, 0xFFFFF6EA);
            g.fill(replaceX0, rowY + 2, replaceX1, rowY + 18, canEdit ? 0xCC3D8B5A : 0x66888888);
            g.drawCenteredString(font,
                    Component.translatable("gui.touhou_maid_construction.missing_replace_as").getString(),
                    (replaceX0 + replaceX1) / 2, rowY + 6, 0xFFFFF6EA);
            if (replacement.isPresent()) {
                g.fill(clearX0, rowY + 2, clearX1, rowY + 18, canEdit ? 0xCCB54A3A : 0x66888888);
                g.drawCenteredString(font,
                        Component.translatable("gui.touhou_maid_construction.missing_replace_clear").getString(),
                        (clearX0 + clearX1) / 2, rowY + 6, 0xFFFFF6EA);
            }

            boolean highlighting = missingId.equals(SitePickController.highlightMissingBlockId());
            if (highlighting) {
                g.fill(contentX + 2, rowY, contentX + 4, rowY + rowH - 2, DANGER);
            }

            missingReplaceHits.add(new MissingReplaceRowHit(
                    missingId,
                    showX0, rowY + 2, showX1, rowY + 18,
                    replaceX0, rowY + 2, replaceX1, rowY + 18,
                    clearX0, rowY + 2, clearX1, rowY + 18,
                    replacement.isPresent(),
                    canEdit));
            shown++;
        }
    }

    private void renderPage3(GuiGraphics g, int contentX) {
        maidRowHits.clear();
        BuildProject project = selected().orElse(null);
        maidPanelTop = top + 56;
        maidPanelBottom = top + guiH - PROGRESS_BLOCK_H;
        maidPanelW = left + guiW - 8 - contentX;

        g.drawString(font, Component.translatable("gui.touhou_maid_construction.maids_header").getString(),
                contentX, maidPanelTop, TEXT, false);
        maidPanelTop += LINE_H + 2;

        g.fill(contentX, maidPanelTop, left + guiW - 8, maidPanelBottom, PARCHMENT_DARK);

        int visibleRows = Math.max(1, (maidPanelBottom - maidPanelTop) / MAID_ROW_H);
        int maxMaidScroll = Math.max(0, nearbyMaids.size() - visibleRows);
        maidScroll = Mth.clamp(maidScroll, 0, maxMaidScroll);

        Optional<BuildProject> selectedProject = selected();
        int row = 0;
        for (int i = maidScroll; i < nearbyMaids.size() && row < visibleRows; i++, row++) {
            BuildJobService.MaidInfo maid = nearbyMaids.get(i);
            int rowY = maidPanelTop + row * MAID_ROW_H;
            boolean picked = selectedProject.isPresent()
                    && selectedProject.get().assignedMaids().contains(maid.id());
            int rowBg = picked ? 0x44C4783A : 0x00000000;
            g.fill(contentX + 2, rowY, left + guiW - 10, rowY + MAID_ROW_H - 1, rowBg);

            EntityMaid entity = findClientMaid(maid.id());
            int headX = contentX + 6;
            int headY = rowY + 4;
            if (entity != null) {
                InventoryScreen.renderEntityInInventoryFollowsAngle(
                        g, headX + 10, headY + 14, 12, 0.35F, 0.25F, entity);
            } else {
                g.fill(headX, headY, headX + 18, headY + 18, 0xFF6B4F2A);
                g.drawString(font, "?", headX + 6, headY + 5, LABEL_CREAM, false);
            }

            String mark = picked ? "[x]" : "[ ]";
            int color = maid.onConstructionTask() ? OK_GREEN : TEXT;
            g.drawString(font, mark + " " + trim(maid.name(), 16), contentX + 28, rowY + 4, color, false);
            if (picked) {
                String wait = maid.materialWaitBlockId();
                String rowStatus;
                int rowStatusColor;
                if (wait != null && !wait.isEmpty()) {
                    rowStatus = Component.translatable(
                            "gui.touhou_maid_construction.maid_status.waiting_material",
                            MaterialDisplay.nameForMaterialRequest(wait)).getString();
                    rowStatusColor = DANGER;
                } else if (project != null && project.status() == BuildProject.Status.BUILDING
                        && maid.onConstructionTask()) {
                    rowStatus = Component.translatable(
                            "gui.touhou_maid_construction.maid_status.building").getString();
                    rowStatusColor = OK_GREEN;
                } else {
                    rowStatus = Component.translatable(
                            "gui.touhou_maid_construction.maid_status.selected").getString();
                    rowStatusColor = MUTED;
                }
                g.drawString(font, trim(rowStatus, 30), contentX + 28, rowY + 14, rowStatusColor, false);
            }
            maidRowHits.add(new MaidRowHit(
                    maid.id(), contentX + 2, rowY, left + guiW - 10, rowY + MAID_ROW_H));
        }

        if (nearbyMaids.isEmpty()) {
            g.drawString(font, Component.translatable("gui.touhou_maid_construction.no_maid_nearby").getString(),
                    contentX + 6, maidPanelTop + 6, MUTED, false);
        } else if (nearbyMaids.size() > visibleRows) {
            g.drawString(font, Component.translatable("gui.touhou_maid_construction.maid_scroll_hint").getString(),
                    contentX + 6, maidPanelBottom - 10, MUTED, false);
        }

        int barAreaTop = top + guiH - PROGRESS_BLOCK_H + 6;
        String status = project == null
                ? "-"
                : Component.translatable(
                        "gui.touhou_maid_construction.status." + project.status().name().toLowerCase()).getString();
        int pct = project == null ? 0 : project.progressPercent();
        g.drawString(font, Component.translatable("gui.touhou_maid_construction.progress_bar", pct, status).getString(),
                contentX, barAreaTop, TEXT, false);

        int barY = barAreaTop + LINE_H + 2;
        int barW = maidPanelW - 4;
        int barH = PROGRESS_BAR_H;
        g.fill(contentX, barY, contentX + barW, barY + barH, 0xFF5C4630);
        if (pct > 0) {
            int fill = Math.max(1, barW * pct / 100);
            g.fill(contentX, barY, contentX + fill, barY + barH, OK_GREEN);
        }
        g.fill(contentX, barY, contentX + barW, barY + 1, WOOD_BORDER);
        g.fill(contentX, barY + barH - 1, contentX + barW, barY + barH, WOOD_BORDER);

        int consumedY = barY + barH + 4;
        g.drawString(font, Component.translatable("gui.touhou_maid_construction.consumed",
                        project == null ? 0 : project.consumedMaterials()).getString(),
                contentX, consumedY, MUTED, false);
        if (project != null && project.status() == BuildProject.Status.PAUSED) {
            g.drawString(font, Component.translatable("gui.touhou_maid_construction.manual_pause_hint").getString(),
                    contentX, consumedY + LINE_H, DANGER, false);
        } else if (project != null && summary != null && vaultShortage(project)) {
            g.drawString(font, truncateForLine(
                    Component.translatable("gui.touhou_maid_construction.vault_shortage_hint").getString(),
                    maidPanelW - 4), contentX, consumedY + LINE_H, DANGER, false);
        }
    }

    private static final int LABEL_CREAM = 0xFFFFF6EA;
    private static final int WOOD_BORDER = 0xFF6B4F2A;

    private EntityMaid findClientMaid(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return null;
        }
        for (EntityMaid maid : mc.level.getEntitiesOfClass(
                EntityMaid.class, mc.player.getBoundingBox().inflate(128))) {
            if (maid.getUUID().equals(id)) {
                return maid;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (page == 1 && button == 0
                && mouseX >= dropX && mouseX <= dropX + dropW
                && mouseY >= dropY && mouseY <= dropY + dropH) {
            importFromPicker();
            return true;
        }
        if (page == 3 && button == 0) {
            for (MaidRowHit hit : maidRowHits) {
                if (hit.contains(mouseX, mouseY)) {
                    sendToggleMaid(hit.maidId());
                    selected().ifPresent(p -> p.toggleAssignedMaid(hit.maidId()));
                    rebuildHubButtons();
                    return true;
                }
            }
        }
        if (page == 2 && button == 0) {
            for (MaterialCellHit hit : materialCellHits) {
                if (hit.actionContains(mouseX, mouseY)) {
                    if (MaterialBlockPolicy.isAutoIgnored(hit.line().blockId())) {
                        return true;
                    }
                    if (hit.ignored()) {
                        sendRestoreMaterial(hit.line().blockId());
                        selected().ifPresent(p -> p.restoreIgnoredBlock(hit.line().blockId()));
                    } else {
                        sendIgnoreMaterial(hit.line().blockId());
                        selected().ifPresent(p -> p.ignoreBlock(hit.line().blockId()));
                    }
                    return true;
                }
            }
        }
        if (page == 4 && button == 0) {
            for (MissingReplaceRowHit hit : missingReplaceHits) {
                if (hit.showContains(mouseX, mouseY)) {
                    BuildProject project = selected().orElse(null);
                    boolean turningOff = hit.missingId().equals(SitePickController.highlightMissingBlockId());
                    boolean hasAnchor = project != null && project.anchor() != null;
                    if (!turningOff && !GhostPreviewPolicy.canArmMissingHighlight(hasAnchor)) {
                        statusLine = Component.translatable(
                                "message.touhou_maid_construction.missing_show_need_site").getString();
                        return true;
                    }
                    if (!turningOff && project != null) {
                        SitePickController.ensureAnchoredPreview(
                                project.schematicPath(),
                                project.anchor(),
                                project.coverTerrain(),
                                project.placementYaw(),
                                project.startLayer());
                    }
                    SitePickController.toggleHighlightMissingBlock(hit.missingId(), itemMode);
                    onClose();
                    return true;
                }
                if (hit.replaceContains(mouseX, mouseY)) {
                    if (!hit.canEdit()) {
                        statusLine = Component.translatable(
                                "message.touhou_maid_construction.missing_replace_locked").getString();
                        return true;
                    }
                    MissingReplacePickController.begin(pos, itemMode, hand, hit.missingId());
                    onClose();
                    return true;
                }
                if (hit.hasClear() && hit.clearContains(mouseX, mouseY)) {
                    if (!hit.canEdit()) {
                        statusLine = Component.translatable(
                                "message.touhou_maid_construction.missing_replace_locked").getString();
                        return true;
                    }
                    ModNetwork.CHANNEL.sendToServer(itemMode
                            ? TerminalActionPacket.clearMissingReplaceItem(hand, hit.missingId())
                            : TerminalActionPacket.clearMissingReplace(pos, hit.missingId()));
                    selected().ifPresent(p -> p.clearMissingBlockReplacement(hit.missingId()));
                    reloadSummary();
                    return true;
                }
            }
        }
        if (mouseX >= left + 4 && mouseX <= left + sideW - 4) {
            int y = top + 44;
            int shown = 0;
            int maxRows = Math.max(5, (guiH - 60) / 20);
            for (int i = leftScroll; i < projects.size() && shown < maxRows; i++, shown++) {
                if (mouseY >= y && mouseY <= y + 18) {
                    BuildProject project = projects.get(i);
                    selectProject(project.id());
                    sendSelect(project.id());
                    return true;
                }
                y += 20;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < left + sideW) {
            leftScroll = Mth.clamp(leftScroll - (int) Math.signum(delta), 0, Math.max(0, projects.size() - 1));
            return true;
        }
        if (page == 2 && summary != null && materialPanelW > 0) {
            int cols = MATERIAL_COLS;
            int headerEnd = materialPanelTop + 4 + LINE_H * 3 + 2;
            int listBottom = materialPanelBottom - MATERIAL_FOOTER_H;
            int maxRows = Math.max(1, (listBottom - headerEnd) / MATERIAL_ROW_H);
            int pageSize = Math.max(1, cols * maxRows);
            materialScroll = Mth.clamp(
                    materialScroll - (int) Math.signum(delta) * Math.max(1, cols),
                    0,
                    Math.max(0, summary.lines().size() - pageSize));
            if (selectedId != null && !selectedId.isEmpty()) {
                HubSessionCache.setMaterialScroll(selectedId, materialScroll);
            }
            return true;
        }
        if (page == 3 && mouseX >= left + sideW && maidPanelW > 0) {
            int visibleRows = Math.max(1, (maidPanelBottom - maidPanelTop) / MAID_ROW_H);
            int maxScroll = Math.max(0, nearbyMaids.size() - visibleRows);
            maidScroll = Mth.clamp(maidScroll - (int) Math.signum(delta), 0, maxScroll);
            if (selectedId != null && !selectedId.isEmpty()) {
                HubSessionCache.setMaidScroll(selectedId, maidScroll);
            }
            return true;
        }
        if (page == 4 && rawSummary != null) {
            int maxRows = Math.max(1, (guiH - 60) / 22);
            int maxScroll = Math.max(0, rawSummary.missingBlocks().size() - maxRows);
            missingReplaceScroll = Mth.clamp(
                    missingReplaceScroll - (int) Math.signum(delta), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private boolean vaultShortage(BuildProject project) {
        if (summary == null || project.status() == BuildProject.Status.DONE) {
            return false;
        }
        for (SchematicFiles.MaterialLine line : summary.lines()) {
            if (project.isIgnoredBlock(line.blockId())
                    || MaterialBlockPolicy.isAutoFree(line.blockId())) {
                continue;
            }
            int required = requiredBlockCount(project, line);
            int have = VaultCounts.haveForBlockId(vaultCounts, line.blockId());
            if (have < required) {
                return true;
            }
        }
        return false;
    }

    private String truncateForLine(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "…";
        int limit = text.length();
        while (limit > 0 && font.width(text.substring(0, limit)) + font.width(ellipsis) > maxWidth) {
            limit--;
        }
        return limit > 0 ? text.substring(0, limit) + ellipsis : ellipsis;
    }

    private static String trim(String s, int max) {
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, Math.max(0, max - 1)) + "…";
    }

    private record MaidRowHit(UUID maidId, int x0, int y0, int x1, int y1) {
        boolean contains(double mx, double my) {
            return mx >= x0 && mx < x1 && my >= y0 && my < y1;
        }
    }

    private record MaterialCellHit(
            SchematicFiles.MaterialLine line,
            int x0, int y0, int x1, int y1,
            int actionX0, int actionY0, int actionX1, int actionY1,
            boolean ignored) {
        boolean contains(double mx, double my) {
            return mx >= x0 && mx < x1 && my >= y0 && my < y1;
        }

        boolean actionContains(double mx, double my) {
            return mx >= actionX0 && mx < actionX1 && my >= actionY0 && my < actionY1;
        }
    }

    private record MissingReplaceRowHit(
            String missingId,
            int showX0, int showY0, int showX1, int showY1,
            int replaceX0, int replaceY0, int replaceX1, int replaceY1,
            int clearX0, int clearY0, int clearX1, int clearY1,
            boolean hasClear,
            boolean canEdit) {
        boolean showContains(double mx, double my) {
            return mx >= showX0 && mx < showX1 && my >= showY0 && my < showY1;
        }

        boolean replaceContains(double mx, double my) {
            return mx >= replaceX0 && mx < replaceX1 && my >= replaceY0 && my < replaceY1;
        }

        boolean clearContains(double mx, double my) {
            return hasClear && mx >= clearX0 && mx < clearX1 && my >= clearY0 && my < clearY1;
        }
    }
}
