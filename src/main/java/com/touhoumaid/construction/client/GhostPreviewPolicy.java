package com.touhoumaid.construction.client;

/**
 * Site-pick ghosts and missing-block ghosts must not share one filter or one prompt.
 * Site pick always draws the full schematic; missing highlight is a separate mode.
 */
public final class GhostPreviewPolicy {
    public enum Hud {
        NONE,
        SITE_PICK,
        MISSING_HIGHLIGHT,
        REPLACE_PICK
    }

    private GhostPreviewPolicy() {
    }

    /**
     * Id passed into the ghost renderer. Empty means draw every schematic cell.
     * A leftover missing-block id must not hide the site-pick blueprint.
     */
    public static String renderHighlight(boolean sitePicking, String highlightId) {
        if (sitePicking) {
            return "";
        }
        return highlightId == null ? "" : highlightId;
    }

    public static Hud hud(boolean replacePicking, boolean sitePicking, String highlightId) {
        if (replacePicking) {
            return Hud.REPLACE_PICK;
        }
        if (sitePicking) {
            return Hud.SITE_PICK;
        }
        if (highlightId != null && !highlightId.isEmpty()) {
            return Hud.MISSING_HIGHLIGHT;
        }
        return Hud.NONE;
    }

    /** Without a confirmed origin the missing-block ghost has nowhere to draw. */
    public static boolean canArmMissingHighlight(boolean hasConfirmedAnchor) {
        return hasConfirmedAnchor;
    }

    /**
     * The missing-block prompt only speaks for a player still holding the terminal it was armed from.
     * Arming from a placed terminal block (no held item involved) keeps the old always-on line.
     */
    public static boolean missingHighlightHudVisible(
            boolean armedFromItemTerminal,
            boolean holdingTerminalItem,
            boolean replacePicking,
            boolean sitePicking,
            String highlightId) {
        if (hud(replacePicking, sitePicking, highlightId) != Hud.MISSING_HIGHLIGHT) {
            return false;
        }
        return !armedFromItemTerminal || holdingTerminalItem;
    }

    /** An item-terminal highlight must not survive as an invisible switch once the item is gone. */
    public static boolean missingHighlightExpired(
            boolean armedFromItemTerminal, boolean holdingTerminalItem) {
        return armedFromItemTerminal && !holdingTerminalItem;
    }
}
