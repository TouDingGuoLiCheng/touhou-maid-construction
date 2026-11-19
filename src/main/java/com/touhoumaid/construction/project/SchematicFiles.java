package com.touhoumaid.construction.project;

import com.touhoumaid.construction.blueprint.Blueprint;
import com.touhoumaid.construction.blueprint.BlueprintLoadReport;
import com.touhoumaid.construction.blueprint.BlueprintRegion;
import com.touhoumaid.construction.blueprint.BlueprintRegistryCheck;
import com.touhoumaid.construction.blueprint.LitematicReader;
import com.touhoumaid.construction.blueprint.RawBlockState;
import com.touhoumaid.construction.build.StartLayerPolicy;
import com.touhoumaid.construction.placement.BlueprintPlacement;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Helpers for schematic folder + material summaries used by the terminal GUI.
 */
public final class SchematicFiles {
    public static final String FOLDER_NAME = "schematics";

    private SchematicFiles() {
    }

    public static Path schematicsDir() {
        return Path.of(FOLDER_NAME).toAbsolutePath().normalize();
    }

    public static void ensureSchematicsDir() throws IOException {
        Files.createDirectories(schematicsDir());
    }

    public static List<String> listLitematicNames() {
        try {
            ensureSchematicsDir();
            List<String> names = new ArrayList<>();
            try (var stream = Files.list(schematicsDir())) {
                stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".litematic"))
                        .map(p -> p.getFileName().toString())
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .forEach(names::add);
            }
            return names;
        } catch (IOException e) {
            return List.of();
        }
    }

    public static Path resolveSchematic(String fileName) {
        Path base = schematicsDir();
        Path resolved = base.resolve(fileName).normalize();
        if (!resolved.startsWith(base)) {
            throw new IllegalArgumentException("Invalid schematic path");
        }
        return resolved;
    }

    /**
     * Copy an external .litematic into the schematics folder (overwrite same name).
     * @return file name inside schematics dir
     */
    public static String copyIntoSchematics(Path source) throws IOException {
        ensureSchematicsDir();
        String name = source.getFileName().toString();
        if (!name.toLowerCase().endsWith(".litematic")) {
            throw new IllegalArgumentException("Not a .litematic file");
        }
        Path dest = resolveSchematic(name);
        Files.copy(source, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return name;
    }

    public static List<String> copyFolderLitematics(Path folder) throws IOException {
        ensureSchematicsDir();
        List<String> imported = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return imported;
        }
        try (var stream = Files.list(folder)) {
            stream.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".litematic"))
                    .forEach(p -> {
                        try {
                            imported.add(copyIntoSchematics(p));
                        } catch (IOException ignored) {
                        }
                    });
        }
        return imported;
    }

    public static MaterialSummary summarize(Path path) throws IOException {
        return summarize(path, 0);
    }

    public static MaterialSummary summarize(Path path, int startLayer) throws IOException {
        Blueprint blueprint = LitematicReader.read(path);
        BlueprintLoadReport report = BlueprintRegistryCheck.check(blueprint);
        Map<String, Integer> counts = new LinkedHashMap<>();
        int totalIncluded = 0;
        BlueprintPlacement.Footprint footprint = BlueprintPlacement.unrotatedFootprint(blueprint);
        int clampedStart = StartLayerPolicy.clamp(startLayer, footprint.sizeY());
        for (BlueprintRegion region : blueprint.regions().values()) {
            Vec3i size = region.size();
            for (int y = 0; y < size.getY(); y++) {
                int schematicY = BlueprintPlacement.schematicY(blueprint, region, y);
                if (!StartLayerPolicy.includesLocalY(schematicY, clampedStart)) {
                    continue;
                }
                for (int z = 0; z < size.getZ(); z++) {
                    for (int x = 0; x < size.getX(); x++) {
                        RawBlockState state = region.blockAt(x, y, z);
                        if ("minecraft:air".equals(state.name())) {
                            continue;
                        }
                        counts.merge(state.name(), 1, Integer::sum);
                        totalIncluded++;
                    }
                }
            }
        }
        List<MaterialLine> lines = new ArrayList<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> lines.add(new MaterialLine(e.getKey(), e.getValue())));
        return new MaterialSummary(
                blueprint.name(),
                blueprint.author(),
                totalIncluded,
                blueprint.enclosingSize().getX(),
                blueprint.enclosingSize().getY(),
                blueprint.enclosingSize().getZ(),
                lines,
                report.missingBlocks(),
                report.missingEntityTypes(),
                report.notes());
    }

    /** Apply project-level missing-block replacements to a summary for display / fill / vault need. */
    public static MaterialSummary applyReplacements(MaterialSummary summary, Map<String, String> replacements) {
        if (summary == null) {
            return null;
        }
        if (replacements == null || replacements.isEmpty()) {
            return summary;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (MaterialLine line : summary.lines()) {
            counts.merge(line.blockId(), line.count(), Integer::sum);
        }
        Map<String, Integer> remappedCounts = MissingBlockReplacePolicy.remapMaterialCounts(counts, replacements);
        List<MaterialLine> lines = new ArrayList<>();
        remappedCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(e -> lines.add(new MaterialLine(e.getKey(), e.getValue())));
        List<String> missing = MissingBlockReplacePolicy.unresolvedMissing(
                summary.missingBlocks(), replacements);
        return new MaterialSummary(
                summary.blueprintName(),
                summary.author(),
                summary.totalBlocks(),
                summary.sizeX(),
                summary.sizeY(),
                summary.sizeZ(),
                lines,
                missing,
                summary.missingEntities(),
                summary.notes());
    }

    public record MaterialLine(String blockId, int count) {
        public ItemStack iconStack() {
            return MaterialDisplay.iconForBlockId(blockId);
        }

        public String displayName() {
            return MaterialDisplay.nameForBlockId(blockId).getString();
        }
    }

    public record MaterialSummary(
            String blueprintName,
            String author,
            int totalBlocks,
            int sizeX,
            int sizeY,
            int sizeZ,
            List<MaterialLine> lines,
            List<String> missingBlocks,
            List<String> missingEntities,
            List<String> notes) {
    }
}
