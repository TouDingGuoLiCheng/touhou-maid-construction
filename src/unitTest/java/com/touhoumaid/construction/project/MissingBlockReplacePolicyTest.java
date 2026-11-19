package com.touhoumaid.construction.project;

import com.touhoumaid.construction.blueprint.RawBlockState;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissingBlockReplacePolicyTest {
    @Test
    void remapKeepsPropertiesAndSwapsName() {
        RawBlockState raw = new RawBlockState(
                "missing_mod:fancy_stairs",
                Map.of("facing", "east", "half", "bottom"));
        RawBlockState remapped = MissingBlockReplacePolicy.remap(
                raw, Map.of("missing_mod:fancy_stairs", "minecraft:oak_stairs"));
        assertEquals("minecraft:oak_stairs", remapped.name());
        assertEquals("east", remapped.properties().get("facing"));
        assertEquals("bottom", remapped.properties().get("half"));
    }

    @Test
    void remapWithoutEntryLeavesRawUnchanged() {
        RawBlockState raw = new RawBlockState("missing_mod:brick", Map.of());
        RawBlockState remapped = MissingBlockReplacePolicy.remap(raw, Map.of());
        assertEquals("missing_mod:brick", remapped.name());
    }

    @Test
    void unresolvedMissingExcludesMappedIds() {
        List<String> unresolved = MissingBlockReplacePolicy.unresolvedMissing(
                List.of("a:one", "a:two", "a:three"),
                Map.of("a:two", "minecraft:stone"));
        assertEquals(List.of("a:one", "a:three"), unresolved);
    }

    @Test
    void materialCountsMergeOntoReplacementId() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("missing_mod:a", 3);
        counts.put("minecraft:stone", 2);
        counts.put("missing_mod:b", 1);
        Map<String, Integer> remapped = MissingBlockReplacePolicy.remapMaterialCounts(
                counts, Map.of("missing_mod:a", "minecraft:stone"));
        assertEquals(5, remapped.get("minecraft:stone"));
        assertEquals(1, remapped.get("missing_mod:b"));
        assertFalse(remapped.containsKey("missing_mod:a"));
    }

    @Test
    void canEditOnlyWhenNotActivelyBuilding() {
        assertTrue(MissingBlockReplacePolicy.canEditReplacements(BuildProject.Status.DRAFT));
        assertTrue(MissingBlockReplacePolicy.canEditReplacements(BuildProject.Status.SITE_READY));
        assertTrue(MissingBlockReplacePolicy.canEditReplacements(BuildProject.Status.PAUSED));
        assertTrue(MissingBlockReplacePolicy.canEditReplacements(BuildProject.Status.DONE));
        assertFalse(MissingBlockReplacePolicy.canEditReplacements(BuildProject.Status.BUILDING));
    }

    @Test
    void effectiveBlockIdUsesReplacementWhenPresent() {
        assertEquals(
                "minecraft:cobblestone",
                MissingBlockReplacePolicy.effectiveBlockId(
                        "missing_mod:x", Map.of("missing_mod:x", "minecraft:cobblestone")));
        assertEquals(
                "missing_mod:x",
                MissingBlockReplacePolicy.effectiveBlockId("missing_mod:x", Map.of()));
    }
}
