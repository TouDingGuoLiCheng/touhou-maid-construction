package com.touhoumaid.construction.client;

import com.touhoumaid.construction.blueprint.Blueprint;
import com.touhoumaid.construction.blueprint.LitematicReader;
import com.touhoumaid.construction.item.ConstructionTerminalItem;
import com.touhoumaid.construction.network.ConfirmSitePacket;
import com.touhoumaid.construction.network.ModNetwork;
import com.touhoumaid.construction.placement.ConflictScanner;
import com.touhoumaid.construction.placement.PlacementRules;
import com.touhoumaid.construction.placement.SchematicYaw;
import com.touhoumaid.construction.project.SchematicFiles;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Client site-pick + persistent ghost outline after confirm / during build.
 */
@OnlyIn(Dist.CLIENT)
public final class SitePickController {
    public enum Mode {
        IDLE,
        PICKING,
        ANCHORED,
        BUILDING
    }

    private static Mode mode = Mode.IDLE;
    private static BlockPos terminalPos;
    private static String projectId = "";
    private static String schematicPath = "";
    private static boolean coverTerrain;
    private static boolean ignoreOverlaps;
    private static int startLayer;
    private static boolean itemMode;
    private static InteractionHand hand = InteractionHand.MAIN_HAND;
    private static Blueprint blueprint;
    private static BlockPos hoverClicked;
    private static BlockPos hoverOrigin;
    private static BlockPos lockedOrigin;
    private static ConflictScanner.Result conflicts = new ConflictScanner.Result(0, java.util.List.of());
    private static boolean previewVisible = true;
    private static boolean prefsLoaded;
    private static boolean pickFrozen = false;
    private static BlockPos frozenClicked;
    private static BlockPos frozenOrigin;
    private static ConflictScanner.Result frozenConflicts = new ConflictScanner.Result(0, java.util.List.of());
    private static final Set<BlockPos> placedCells = new HashSet<>();
    private static int placedGeneration;
    private static int placementYaw = 0;
    private static int lastScannedYaw = Integer.MIN_VALUE;
    /** When non-empty, ghost render paints matching schematic cells in red. */
    private static String highlightMissingBlockId = "";
    /** Armed from the held terminal item, so the highlight expires with the item. */
    private static boolean highlightNeedsTerminalItem;

    private SitePickController() {
    }

    public static Mode mode() {
        return mode;
    }

    public static boolean isPicking() {
        return mode == Mode.PICKING;
    }

    public static boolean shouldRenderGhost() {
        ensurePrefsLoaded();
        return previewVisible
                && blueprint != null
                && ghostOrigin() != null
                && (mode == Mode.PICKING || mode == Mode.ANCHORED || mode == Mode.BUILDING);
    }

    public static boolean isActive() {
        return mode == Mode.PICKING;
    }

    public static Blueprint blueprint() {
        return blueprint;
    }

    public static BlockPos hoverOrigin() {
        return hoverOrigin;
    }

    public static BlockPos ghostOrigin() {
        if (mode == Mode.PICKING) {
            if (pickFrozen && frozenOrigin != null) {
                return frozenOrigin;
            }
            return hoverOrigin;
        }
        return lockedOrigin;
    }

    public static boolean isPickFrozen() {
        return pickFrozen;
    }

    public static int placementYaw() {
        return placementYaw;
    }

    public static void setPlacementYaw(int yaw) {
        placementYaw = SchematicYaw.normalize(yaw);
        lastScannedYaw = Integer.MIN_VALUE;
    }

    public static void rotateLeft() {
        if (mode != Mode.PICKING) {
            return;
        }
        setPlacementYaw(SchematicYaw.rotateLeft(placementYaw));
    }

    public static void rotateRight() {
        if (mode != Mode.PICKING) {
            return;
        }
        setPlacementYaw(SchematicYaw.rotateRight(placementYaw));
    }

    private static void rescanAfterYawChange() {
        Minecraft mc = Minecraft.getInstance();
        BlockPos clicked = pickFrozen ? frozenClicked : hoverClicked;
        BlockPos origin = pickFrozen ? frozenOrigin : hoverOrigin;
        if (clicked == null || origin == null || blueprint == null || mc.level == null) {
            return;
        }
        ConflictScanner.Result result = ConflictScanner.scan(
                mc.level, blueprint, origin, coverTerrain, ignoreOverlaps, placementYaw, startLayer);
        if (pickFrozen) {
            frozenConflicts = result;
        } else {
            conflicts = result;
        }
        lastScannedYaw = placementYaw;
    }

    public static void togglePickFreeze() {
        if (mode != Mode.PICKING) {
            return;
        }
        pickFrozen = !pickFrozen;
        if (pickFrozen) {
            if (hoverOrigin == null || hoverClicked == null) {
                pickFrozen = false;
                return;
            }
            frozenClicked = hoverClicked.immutable();
            frozenOrigin = hoverOrigin.immutable();
            frozenConflicts = conflicts;
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.translatable("message.touhou_maid_construction.site_pick_frozen"), true);
            }
        } else {
            frozenClicked = null;
            frozenOrigin = null;
            frozenConflicts = new ConflictScanner.Result(0, java.util.List.of());
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        Component.translatable("message.touhou_maid_construction.site_pick_unfrozen"), true);
            }
        }
    }

    public static boolean coverTerrain() {
        return coverTerrain;
    }

    public static boolean ignoreOverlaps() {
        return ignoreOverlaps;
    }

    public static int startLayer() {
        return startLayer;
    }

    public static void setStartLayer(int layer) {
        startLayer = Math.max(0, layer);
    }

    public static ConflictScanner.Result conflicts() {
        return conflicts;
    }

    public static boolean previewVisible() {
        ensurePrefsLoaded();
        return previewVisible;
    }

    public static void setPreviewVisible(boolean visible) {
        ensurePrefsLoaded();
        previewVisible = visible;
        persistPrefs();
    }

    /** Load outline preference from the client game directory (once). */
    public static void ensurePrefsLoaded() {
        if (prefsLoaded) {
            return;
        }
        prefsLoaded = true;
        Path file = prefsFile();
        if (file != null) {
            previewVisible = PreviewVisibilityPrefs.load(file, true);
        }
    }

    private static void persistPrefs() {
        Path file = prefsFile();
        if (file == null) {
            return;
        }
        try {
            PreviewVisibilityPrefs.save(file, previewVisible);
        } catch (Exception ignored) {
        }
    }

    private static Path prefsFile() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null) {
            return null;
        }
        return mc.gameDirectory.toPath().resolve(PreviewVisibilityPrefs.FILE_NAME);
    }

    public static String highlightMissingBlockId() {
        return highlightMissingBlockId;
    }

    public static String highlightHudLine() {
        if (!GhostPreviewPolicy.missingHighlightHudVisible(
                highlightNeedsTerminalItem,
                holdsTerminalItem(),
                MissingReplacePickController.isActive(),
                isPicking(),
                highlightMissingBlockId)) {
            return "";
        }
        return Component.translatable(
                "gui.touhou_maid_construction.missing_show_hud", highlightMissingBlockId).getString();
    }

    private static boolean holdsTerminalItem() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return false;
        }
        return mc.player.getMainHandItem().getItem() instanceof ConstructionTerminalItem
                || mc.player.getOffhandItem().getItem() instanceof ConstructionTerminalItem;
    }

    /**
     * Armed from the item terminal, the highlight follows the item: switch it away and the prompt
     * and filtered ghost stop instead of pinning the screen until the GUI is reopened.
     */
    public static void tickHighlightGuard() {
        if (highlightMissingBlockId.isEmpty() || !highlightNeedsTerminalItem) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (GhostPreviewPolicy.missingHighlightExpired(
                highlightNeedsTerminalItem, holdsTerminalItem())) {
            clearHighlightMissingBlock();
        }
    }

    public static void toggleHighlightMissingBlock(String blockId, boolean itemTerminal) {
        MissingReplacePickController.deactivateQuiet();
        if (blockId == null || blockId.isEmpty()) {
            clearHighlightMissingBlock();
            return;
        }
        if (blockId.equals(highlightMissingBlockId)) {
            clearHighlightMissingBlock();
            actionBar(Component.translatable("message.touhou_maid_construction.missing_show_off"));
        } else {
            highlightMissingBlockId = blockId;
            highlightNeedsTerminalItem = itemTerminal;
            ensurePrefsLoaded();
            previewVisible = PreviewVisibilityPolicy.afterArmMissingHighlight();
            actionBar(Component.translatable(
                    "message.touhou_maid_construction.missing_show_hint", blockId));
        }
    }

    public static void clearHighlightMissingBlock() {
        highlightMissingBlockId = "";
        highlightNeedsTerminalItem = false;
    }

    /**
     * Leave site-pick so another client mode (missing highlight / replace pick)
     * does not keep the site prompt and the filtered ghost at the same time.
     */
    public static void yieldToOtherClientMode() {
        clearHighlightMissingBlock();
        if (mode != Mode.PICKING) {
            return;
        }
        pickFrozen = false;
        frozenClicked = null;
        frozenOrigin = null;
        frozenConflicts = new ConflictScanner.Result(0, java.util.List.of());
        hoverClicked = null;
        hoverOrigin = null;
        conflicts = new ConflictScanner.Result(0, java.util.List.of());
        mode = lockedOrigin != null ? Mode.ANCHORED : Mode.IDLE;
    }

    private static void actionBar(Component component) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(component, true);
        }
    }

    /**
     * Ensure an anchored ghost exists so missing-block highlight can render after site confirm.
     */
    public static void ensureAnchoredPreview(
            String schematicFile,
            BlockPos origin,
            boolean coverTerrainFlag,
            int yaw,
            int layer) {
        if (schematicFile == null || schematicFile.isEmpty() || origin == null) {
            return;
        }
        ensureLoaded(schematicFile, coverTerrainFlag);
        if (blueprint == null) {
            return;
        }
        startLayer = Math.max(0, layer);
        if (mode == Mode.IDLE || lockedOrigin == null || !lockedOrigin.equals(origin)) {
            lockAnchored(origin, yaw);
        } else {
            placementYaw = SchematicYaw.normalize(yaw);
            ensurePrefsLoaded();
            previewVisible = PreviewVisibilityPolicy.afterRestoreOrSync(previewVisible);
        }
    }

    public static void togglePreviewVisible() {
        ensurePrefsLoaded();
        previewVisible = PreviewVisibilityPolicy.afterToggle(previewVisible);
        persistPrefs();
    }

    public static Set<BlockPos> placedCells() {
        return Collections.unmodifiableSet(placedCells);
    }

    /** Bumps when the placed-cell set actually changes, so the build ghost can drop finished cells. */
    public static int placedGeneration() {
        return placedGeneration;
    }

    public static void markPlaced(BlockPos worldPos) {
        if (placedCells.add(worldPos.immutable())) {
            placedGeneration++;
        }
    }

    public static void clearPlaced() {
        if (!placedCells.isEmpty()) {
            placedCells.clear();
            placedGeneration++;
        }
    }

    public static void begin(
            BlockPos terminal,
            String projectId,
            String schematicFile,
            boolean coverTerrain,
            boolean ignoreOverlaps,
            int startLayer,
            boolean itemMode,
            InteractionHand hand) {
        MissingReplacePickController.deactivateQuiet();
        clearHighlightMissingBlock();
        if (!loadBlueprint(schematicFile)) {
            return;
        }
        mode = Mode.PICKING;
        terminalPos = terminal.immutable();
        SitePickController.projectId = projectId;
        schematicPath = schematicFile;
        SitePickController.coverTerrain = coverTerrain;
        SitePickController.ignoreOverlaps = ignoreOverlaps;
        SitePickController.startLayer = Math.max(0, startLayer);
        SitePickController.itemMode = itemMode;
        SitePickController.hand = hand == null ? InteractionHand.MAIN_HAND : hand;
        hoverClicked = null;
        hoverOrigin = null;
        lockedOrigin = null;
        pickFrozen = false;
        frozenClicked = null;
        frozenOrigin = null;
        frozenConflicts = new ConflictScanner.Result(0, java.util.List.of());
        conflicts = new ConflictScanner.Result(0, java.util.List.of());
        ensurePrefsLoaded();
        previewVisible = PreviewVisibilityPolicy.afterBeginSitePick();
        clearPlaced();
        Minecraft mc = Minecraft.getInstance();
        placementYaw = mc.player == null
                ? SchematicYaw.NONE
                : SchematicYaw.fromHorizontal2D(mc.player.getDirection().get2DDataValue());
        lastScannedYaw = Integer.MIN_VALUE;
        mc.setScreen(null);
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("message.touhou_maid_construction.site_pick_hint"), true);
        }
    }

    /** Load schematic for anchored/build preview without entering pick mode. */
    public static void ensureLoaded(String schematicFile, boolean coverTerrainFlag) {
        if (blueprint != null && schematicFile.equals(schematicPath)) {
            coverTerrain = coverTerrainFlag;
            return;
        }
        if (!loadBlueprint(schematicFile)) {
            return;
        }
        schematicPath = schematicFile;
        coverTerrain = coverTerrainFlag;
    }

    private static boolean loadBlueprint(String schematicFile) {
        try {
            Path path = SchematicFiles.resolveSchematic(schematicFile);
            blueprint = LitematicReader.read(path);
            return true;
        } catch (Exception e) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(Component.literal("Load schematic failed: " + e.getMessage()), false);
            }
            return false;
        }
    }

    /** Keep outline after a successful conflict-free confirm. */
    public static void lockAnchored(BlockPos origin, int yaw) {
        lockedOrigin = origin.immutable();
        hoverClicked = null;
        hoverOrigin = null;
        conflicts = new ConflictScanner.Result(0, java.util.List.of());
        mode = Mode.ANCHORED;
        ensurePrefsLoaded();
        previewVisible = PreviewVisibilityPolicy.afterRestoreOrSync(previewVisible);
        placementYaw = SchematicYaw.normalize(yaw);
    }

    public static void enterBuilding() {
        if (blueprint == null || lockedOrigin == null) {
            return;
        }
        mode = Mode.BUILDING;
        ensurePrefsLoaded();
        previewVisible = PreviewVisibilityPolicy.afterRestoreOrSync(previewVisible);
    }

    public static void markDone() {
        clearAll();
    }

    public static void cancel() {
        clearAll();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("message.touhou_maid_construction.site_pick_cancel"), true);
        }
    }

    public static void clear() {
        clearAll();
    }

    private static void clearAll() {
        mode = Mode.IDLE;
        terminalPos = null;
        projectId = "";
        schematicPath = "";
        coverTerrain = false;
        ignoreOverlaps = false;
        startLayer = 0;
        itemMode = false;
        hand = InteractionHand.MAIN_HAND;
        blueprint = null;
        hoverClicked = null;
        hoverOrigin = null;
        lockedOrigin = null;
        pickFrozen = false;
        frozenClicked = null;
        frozenOrigin = null;
        frozenConflicts = new ConflictScanner.Result(0, java.util.List.of());
        conflicts = new ConflictScanner.Result(0, java.util.List.of());
        clearPlaced();
        placementYaw = 0;
        lastScannedYaw = Integer.MIN_VALUE;
        clearHighlightMissingBlock();
    }

    public static void tick() {
        if (mode != Mode.PICKING || blueprint == null) {
            return;
        }
        if (pickFrozen) {
            if (lastScannedYaw != placementYaw) {
                rescanAfterYawChange();
            }
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            clearAll();
            return;
        }
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
            hoverClicked = null;
            hoverOrigin = null;
            conflicts = new ConflictScanner.Result(0, java.util.List.of());
            return;
        }
        BlockPos clicked = blockHit.getBlockPos();
        if (clicked.equals(hoverClicked) && lastScannedYaw == placementYaw) {
            return;
        }
        hoverClicked = clicked.immutable();
        hoverOrigin = PlacementRules.originForClick(clicked, coverTerrain);
        conflicts = ConflictScanner.scan(
                mc.level, blueprint, hoverOrigin, coverTerrain, ignoreOverlaps, placementYaw, startLayer);
        lastScannedYaw = placementYaw;
    }

    public static boolean tryConfirm() {
        BlockPos confirmClicked = pickFrozen ? frozenClicked : hoverClicked;
        BlockPos confirmOrigin = pickFrozen ? frozenOrigin : hoverOrigin;
        ConflictScanner.Result confirmConflicts = pickFrozen ? frozenConflicts : conflicts;
        if (mode != Mode.PICKING || confirmClicked == null || confirmOrigin == null || blueprint == null) {
            return false;
        }
        if (confirmConflicts.hasConflicts()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                        confirmConflicts.blockingMessage(coverTerrain, ignoreOverlaps), true);
            }
            return true;
        }
        BlockPos origin = confirmOrigin.immutable();
        ModNetwork.CHANNEL.sendToServer(new ConfirmSitePacket(
                terminalPos, projectId, confirmClicked, coverTerrain, ignoreOverlaps, itemMode, hand, placementYaw));
        lockAnchored(origin, placementYaw);
        pickFrozen = false;
        frozenClicked = null;
        frozenOrigin = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.translatable("message.touhou_maid_construction.site_ok"), true);
        }
        return true;
    }

    public static String hudLine() {
        if (mode != Mode.PICKING) {
            return "";
        }
        BlockPos origin = pickFrozen ? frozenOrigin : hoverOrigin;
        ConflictScanner.Result activeConflicts = pickFrozen ? frozenConflicts : conflicts;
        if (origin == null) {
            return Component.translatable("message.touhou_maid_construction.site_pick_look").getString();
        }
        if (pickFrozen) {
            if (activeConflicts.hasConflicts()) {
                return Component.translatable("message.touhou_maid_construction.site_pick_frozen_bad",
                        activeConflicts.blockingSummary()).getString();
            }
            return Component.translatable("message.touhou_maid_construction.site_pick_frozen_good",
                    origin.getX(), origin.getY(), origin.getZ()).getString();
        }
        if (activeConflicts.hasConflicts()) {
            return activeConflicts.blockingMessage(coverTerrain, ignoreOverlaps).getString();
        }
        return Component.translatable(
                "message.touhou_maid_construction.site_pick_good",
                origin.getX(), origin.getY(), origin.getZ()).getString();
    }
}
