package com.touhoumaid.construction.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GhostPreviewPolicyTest {
    @Test
    void sitePickShowsFullSchematicEvenIfMissingHighlightWasArmed() {
        assertEquals("", GhostPreviewPolicy.renderHighlight(true, "missing_mod:brick"));
        assertEquals(
                GhostPreviewPolicy.Hud.SITE_PICK,
                GhostPreviewPolicy.hud(false, true, "missing_mod:brick"));
    }

    @Test
    void missingHighlightFiltersAndPromptsOnlyWhenSitePickIsIdle() {
        assertEquals("missing_mod:brick", GhostPreviewPolicy.renderHighlight(false, "missing_mod:brick"));
        assertEquals(
                GhostPreviewPolicy.Hud.MISSING_HIGHLIGHT,
                GhostPreviewPolicy.hud(false, false, "missing_mod:brick"));
    }

    @Test
    void replacePickPromptWinsSoClicksAreNotSiteConfirm() {
        assertEquals(
                GhostPreviewPolicy.Hud.REPLACE_PICK,
                GhostPreviewPolicy.hud(true, true, "missing_mod:brick"));
    }

    @Test
    void missingHighlightNeedsAConfirmedAnchorOrThePromptLies() {
        assertFalse(GhostPreviewPolicy.canArmMissingHighlight(false));
        assertTrue(GhostPreviewPolicy.canArmMissingHighlight(true));
    }

    @Test
    void itemTerminalHighlightNeedsTheTerminalInHand() {
        assertTrue(GhostPreviewPolicy.missingHighlightHudVisible(
                true, true, false, false, "minecraft:polished_tuff"));
        assertFalse(GhostPreviewPolicy.missingHighlightHudVisible(
                true, false, false, false, "minecraft:polished_tuff"));
    }

    @Test
    void blockTerminalHighlightIgnoresHands() {
        assertTrue(GhostPreviewPolicy.missingHighlightHudVisible(
                false, false, false, false, "minecraft:polished_tuff"));
    }

    @Test
    void otherClientModesWinOverTheMissingHighlight() {
        assertFalse(GhostPreviewPolicy.missingHighlightHudVisible(
                false, true, false, true, "minecraft:polished_tuff"));
        assertFalse(GhostPreviewPolicy.missingHighlightHudVisible(
                false, true, true, false, "minecraft:polished_tuff"));
        assertFalse(GhostPreviewPolicy.missingHighlightHudVisible(
                false, true, false, false, ""));
    }

    @Test
    void onlyItemTerminalIgnoringDespawnsTheHighlight() {
        assertTrue(GhostPreviewPolicy.missingHighlightExpired(true, false));
        assertFalse(GhostPreviewPolicy.missingHighlightExpired(true, true));
        assertFalse(GhostPreviewPolicy.missingHighlightExpired(false, false));
    }
}
