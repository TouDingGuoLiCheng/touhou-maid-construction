package com.touhoumaid.construction.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewVisibilityPolicyTest {
    @TempDir
    Path tempDir;

    @Test
    void syncAnchoredOrEnterBuildingKeepsUserPreferenceOff() {
        assertFalse(PreviewVisibilityPolicy.afterRestoreOrSync(false));
        assertTrue(PreviewVisibilityPolicy.afterRestoreOrSync(true));
    }

    @Test
    void beginSitePickForcesOutlineOn() {
        assertTrue(PreviewVisibilityPolicy.afterBeginSitePick());
    }

    @Test
    void armMissingHighlightForcesOutlineOn() {
        assertTrue(PreviewVisibilityPolicy.afterArmMissingHighlight());
    }

    @Test
    void toggleFlipsPreference() {
        assertFalse(PreviewVisibilityPolicy.afterToggle(true));
        assertTrue(PreviewVisibilityPolicy.afterToggle(false));
    }

    @Test
    void prefsRoundTripOffAcrossReload() throws Exception {
        Path file = tempDir.resolve("preview_outline.txt");
        PreviewVisibilityPrefs.save(file, false);
        assertFalse(PreviewVisibilityPrefs.load(file, true));
        assertTrue(Files.exists(file));
    }

    @Test
    void prefsMissingFileUsesDefault() {
        Path file = tempDir.resolve("missing.txt");
        assertTrue(PreviewVisibilityPrefs.load(file, true));
        assertFalse(PreviewVisibilityPrefs.load(file, false));
    }
}
