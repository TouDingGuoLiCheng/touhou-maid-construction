package com.touhoumaid.construction.project;

import com.touhoumaid.construction.blueprint.RawBlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure rules for player-specified replacements of registry-missing schematic blocks.
 */
public final class MissingBlockReplacePolicy {
    private MissingBlockReplacePolicy() {
    }

    public static String effectiveBlockId(String blockId, Map<String, String> replacements) {
        if (blockId == null || blockId.isEmpty() || replacements == null || replacements.isEmpty()) {
            return blockId == null ? "" : blockId;
        }
        String replacement = replacements.get(blockId);
        return replacement == null || replacement.isEmpty() ? blockId : replacement;
    }

    public static RawBlockState remap(RawBlockState raw, Map<String, String> replacements) {
        if (raw == null) {
            return null;
        }
        String effective = effectiveBlockId(raw.name(), replacements);
        if (effective.equals(raw.name())) {
            return raw;
        }
        return new RawBlockState(effective, raw.properties());
    }

    public static List<String> unresolvedMissing(List<String> missingBlocks, Map<String, String> replacements) {
        if (missingBlocks == null || missingBlocks.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String id : missingBlocks) {
            if (id == null || id.isEmpty()) {
                continue;
            }
            String replacement = replacements == null ? null : replacements.get(id);
            if (replacement == null || replacement.isEmpty()) {
                out.add(id);
            }
        }
        return List.copyOf(out);
    }

    public static Map<String, Integer> remapMaterialCounts(
            Map<String, Integer> counts,
            Map<String, String> replacements) {
        if (counts == null || counts.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String key = effectiveBlockId(entry.getKey(), replacements);
            out.merge(key, entry.getValue(), Integer::sum);
        }
        return out;
    }

    public static boolean canEditReplacements(BuildProject.Status status) {
        return status != BuildProject.Status.BUILDING;
    }
}
