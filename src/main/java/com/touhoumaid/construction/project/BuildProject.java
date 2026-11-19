package com.touhoumaid.construction.project;

import com.touhoumaid.construction.build.BuildJobKey;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * One construction job bound to a terminal (schematic + site flags).
 */
public final class BuildProject {
    public enum Status {
        DRAFT,
        SITE_READY,
        BUILDING,
        PAUSED,
        DONE
    }

    private final String id;
    private String name;
    private String schematicPath;
    private boolean coverTerrain;
    private boolean ignoreOverlaps;
    /** Schematic-relative start layer (local Y from blueprint bottom); cells below are excluded. */
    private int startLayer;
    private boolean autoCraftMaterials;
    private boolean siteConfirmed;
    private Status status;
    private BlockPos anchor;
    private int placementYaw;
    private int conflictCount;
    private int totalBlocks;
    private int placedBlocks;
    private int consumedMaterials;
    private final Map<String, Integer> placedByBlockId = new HashMap<>();
    private final List<UUID> assignedMaids = new ArrayList<>();
    /** Persisted while BUILDING / PAUSED so jobs survive world reload. */
    private UUID buildOwnerId;
    private BlockPos buildTerminalPos;
    private boolean buildItemMode;
    private InteractionHand buildHand = InteractionHand.MAIN_HAND;
    private final Set<String> ignoredBlockIds = new LinkedHashSet<>();
    /** Registry-missing schematic block id → player-chosen replacement block id (this project only). */
    private final Map<String, String> missingBlockReplacements = new HashMap<>();
    private final List<String> placementFailures = new ArrayList<>();

    public BuildProject(String id, String name, String schematicPath) {
        this.id = id;
        this.name = name;
        this.schematicPath = schematicPath;
        this.coverTerrain = false;
        this.ignoreOverlaps = false;
        this.startLayer = 0;
        this.autoCraftMaterials = false;
        this.siteConfirmed = false;
        this.status = Status.DRAFT;
        this.anchor = null;
        this.conflictCount = 0;
    }

    public static BuildProject createNew(String name, String schematicPath) {
        return new BuildProject(UUID.randomUUID().toString(), name, schematicPath);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public String schematicPath() {
        return schematicPath;
    }

    public void setSchematicPath(String schematicPath) {
        this.schematicPath = schematicPath == null ? "" : schematicPath;
    }

    public boolean coverTerrain() {
        return coverTerrain;
    }

    public void setCoverTerrain(boolean coverTerrain) {
        this.coverTerrain = coverTerrain;
    }

    public boolean ignoreOverlaps() {
        return ignoreOverlaps;
    }

    public void setIgnoreOverlaps(boolean ignoreOverlaps) {
        this.ignoreOverlaps = ignoreOverlaps;
    }

    public int startLayer() {
        return startLayer;
    }

    public void setStartLayer(int startLayer) {
        this.startLayer = Math.max(0, startLayer);
    }

    public boolean autoCraftMaterials() {
        return autoCraftMaterials;
    }

    public void setAutoCraftMaterials(boolean autoCraftMaterials) {
        this.autoCraftMaterials = autoCraftMaterials;
    }

    public boolean siteConfirmed() {
        return siteConfirmed;
    }

    public void setSiteConfirmed(boolean siteConfirmed) {
        this.siteConfirmed = siteConfirmed;
        if (siteConfirmed && status == Status.DRAFT) {
            status = Status.SITE_READY;
        }
        if (!siteConfirmed && status == Status.SITE_READY) {
            status = Status.DRAFT;
        }
    }

    public Status status() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status == null ? Status.DRAFT : status;
    }

    public BlockPos anchor() {
        return anchor;
    }

    public void setAnchor(BlockPos anchor) {
        this.anchor = anchor == null ? null : anchor.immutable();
    }

    public int placementYaw() {
        return placementYaw;
    }

    public void setPlacementYaw(int yaw) {
        this.placementYaw = com.touhoumaid.construction.placement.SchematicYaw.normalize(yaw);
    }

    public int conflictCount() {
        return conflictCount;
    }

    public void setConflictCount(int conflictCount) {
        this.conflictCount = Math.max(0, conflictCount);
    }

    public int totalBlocks() {
        return totalBlocks;
    }

    public void setTotalBlocks(int totalBlocks) {
        this.totalBlocks = Math.max(0, totalBlocks);
    }

    public int placedBlocks() {
        return placedBlocks;
    }

    public void setPlacedBlocks(int placedBlocks) {
        this.placedBlocks = Math.max(0, placedBlocks);
    }

    public int consumedMaterials() {
        return consumedMaterials;
    }

    public void setConsumedMaterials(int consumedMaterials) {
        this.consumedMaterials = Math.max(0, consumedMaterials);
    }

    public Map<String, Integer> placedByBlockId() {
        return Map.copyOf(placedByBlockId);
    }

    public void setPlacedByBlockId(Map<String, Integer> counts) {
        placedByBlockId.clear();
        if (counts != null) {
            counts.forEach((id, count) -> {
                if (id != null && !id.isEmpty() && count != null && count > 0) {
                    placedByBlockId.put(id, count);
                }
            });
        }
    }

    /** Clears in-world build progress when the site anchor changes. */
    public void resetBuildProgress() {
        placedBlocks = 0;
        consumedMaterials = 0;
        placedByBlockId.clear();
        placementFailures.clear();
    }

    public List<String> placementFailures() {
        return List.copyOf(placementFailures);
    }

    public void setPlacementFailures(List<String> failures) {
        placementFailures.clear();
        if (failures != null) {
            for (String line : failures) {
                if (line != null && !line.isEmpty()) {
                    placementFailures.add(line);
                }
            }
        }
    }

    public int remainingForBlock(String blockId, int schematicCount) {
        if (blockId == null || schematicCount <= 0) {
            return schematicCount;
        }
        return Math.max(0, schematicCount - placedByBlockId.getOrDefault(blockId, 0));
    }

    /** Same count shown on the materials page and used by creative fill. */
    public int requiredMaterialCount(String blockId, int schematicCount) {
        if (tracksBuildProgress()) {
            return remainingForBlock(blockId, schematicCount);
        }
        return schematicCount;
    }

    public boolean tracksBuildProgress() {
        return status == Status.BUILDING || status == Status.PAUSED || status == Status.DONE;
    }

    public UUID buildOwnerId() {
        return buildOwnerId;
    }

    public BlockPos buildTerminalPos() {
        return buildTerminalPos;
    }

    public boolean buildItemMode() {
        return buildItemMode;
    }

    public InteractionHand buildHand() {
        return buildHand == null ? InteractionHand.MAIN_HAND : buildHand;
    }

    public void setBuildSession(BuildJobKey key) {
        if (key == null) {
            clearBuildSession();
            return;
        }
        buildOwnerId = key.ownerId();
        buildTerminalPos = key.terminalPos().immutable();
        buildItemMode = key.itemMode();
        buildHand = key.hand() == null ? InteractionHand.MAIN_HAND : key.hand();
    }

    public void clearBuildSession() {
        buildOwnerId = null;
        buildTerminalPos = null;
        buildItemMode = false;
        buildHand = InteractionHand.MAIN_HAND;
    }

    public Optional<BuildJobKey> buildJobKey() {
        if (buildOwnerId == null || buildTerminalPos == null) {
            return Optional.empty();
        }
        return Optional.of(new BuildJobKey(
                id(), buildTerminalPos, buildItemMode, buildHand(), buildOwnerId));
    }

    public Set<String> ignoredBlockIds() {
        return Set.copyOf(ignoredBlockIds);
    }

    public boolean isIgnoredBlock(String blockId) {
        return MaterialBlockPolicy.isAutoIgnored(blockId)
                || (blockId != null && ignoredBlockIds.contains(blockId));
    }

    public void ignoreBlock(String blockId) {
        if (blockId != null && !blockId.isEmpty()) {
            ignoredBlockIds.add(blockId);
        }
    }

    public void restoreIgnoredBlock(String blockId) {
        if (blockId != null) {
            ignoredBlockIds.remove(blockId);
        }
    }

    public Map<String, String> missingBlockReplacements() {
        return Map.copyOf(missingBlockReplacements);
    }

    public Optional<String> replacementForMissing(String missingBlockId) {
        if (missingBlockId == null || missingBlockId.isEmpty()) {
            return Optional.empty();
        }
        String replacement = missingBlockReplacements.get(missingBlockId);
        if (replacement == null || replacement.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(replacement);
    }

    public void setMissingBlockReplacement(String missingBlockId, String replacementBlockId) {
        if (missingBlockId == null || missingBlockId.isEmpty()
                || replacementBlockId == null || replacementBlockId.isEmpty()) {
            return;
        }
        missingBlockReplacements.put(missingBlockId, replacementBlockId);
    }

    public void clearMissingBlockReplacement(String missingBlockId) {
        if (missingBlockId != null) {
            missingBlockReplacements.remove(missingBlockId);
        }
    }

    public List<UUID> assignedMaids() {
        return List.copyOf(assignedMaids);
    }

    public void setAssignedMaids(List<UUID> ids) {
        assignedMaids.clear();
        if (ids != null) {
            assignedMaids.addAll(ids);
        }
    }

    public void toggleAssignedMaid(UUID maidId) {
        if (maidId == null) {
            return;
        }
        if (assignedMaids.contains(maidId)) {
            assignedMaids.remove(maidId);
        } else {
            assignedMaids.add(maidId);
        }
    }

    public int progressPercent() {
        if (totalBlocks <= 0) {
            return 0;
        }
        return (int) (100L * placedBlocks / totalBlocks);
    }

    public boolean hasConflicts() {
        return conflictCount > 0;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", id);
        tag.putString("Name", name);
        tag.putString("SchematicPath", schematicPath);
        tag.putBoolean("CoverTerrain", coverTerrain);
        tag.putBoolean("IgnoreOverlaps", ignoreOverlaps);
        tag.putInt("StartLayer", startLayer);
        tag.putBoolean("AutoCraftMaterials", autoCraftMaterials);
        tag.putBoolean("SiteConfirmed", siteConfirmed);
        tag.putInt("PlacementYaw", placementYaw);
        tag.putString("Status", status.name());
        tag.putInt("ConflictCount", conflictCount);
        tag.putInt("TotalBlocks", totalBlocks);
        tag.putInt("PlacedBlocks", placedBlocks);
        tag.putInt("ConsumedMaterials", consumedMaterials);
        ListTag placedList = new ListTag();
        for (Map.Entry<String, Integer> entry : placedByBlockId.entrySet()) {
            CompoundTag placedTag = new CompoundTag();
            placedTag.putString("BlockId", entry.getKey());
            placedTag.putInt("Count", entry.getValue());
            placedList.add(placedTag);
        }
        tag.put("PlacedByBlockId", placedList);
        ListTag maidList = new ListTag();
        for (UUID maidId : assignedMaids) {
            CompoundTag maidTag = new CompoundTag();
            maidTag.putUUID("Id", maidId);
            maidList.add(maidTag);
        }
        tag.put("AssignedMaids", maidList);
        if (anchor != null) {
            tag.putInt("Ax", anchor.getX());
            tag.putInt("Ay", anchor.getY());
            tag.putInt("Az", anchor.getZ());
            tag.putBoolean("HasAnchor", true);
        }
        if (buildOwnerId != null && buildTerminalPos != null) {
            tag.putBoolean("HasBuildSession", true);
            tag.putUUID("BuildOwnerId", buildOwnerId);
            tag.putBoolean("BuildItemMode", buildItemMode);
            tag.putString("BuildHand", buildHand.name());
            tag.putInt("BuildTx", buildTerminalPos.getX());
            tag.putInt("BuildTy", buildTerminalPos.getY());
            tag.putInt("BuildTz", buildTerminalPos.getZ());
        }
        if (!ignoredBlockIds.isEmpty()) {
            ListTag ignored = new ListTag();
            for (String blockId : ignoredBlockIds) {
                ignored.add(net.minecraft.nbt.StringTag.valueOf(blockId));
            }
            tag.put("IgnoredBlocks", ignored);
        }
        if (!missingBlockReplacements.isEmpty()) {
            ListTag replacements = new ListTag();
            for (Map.Entry<String, String> entry : missingBlockReplacements.entrySet()) {
                CompoundTag row = new CompoundTag();
                row.putString("Missing", entry.getKey());
                row.putString("Replacement", entry.getValue());
                replacements.add(row);
            }
            tag.put("MissingBlockReplacements", replacements);
        }
        if (!placementFailures.isEmpty()) {
            ListTag failures = new ListTag();
            for (String line : placementFailures) {
                failures.add(net.minecraft.nbt.StringTag.valueOf(line));
            }
            tag.put("PlacementFailures", failures);
        }
        return tag;
    }

    public static BuildProject load(CompoundTag tag) {
        BuildProject project = new BuildProject(
                tag.getString("Id"),
                tag.getString("Name"),
                tag.getString("SchematicPath"));
        project.coverTerrain = tag.getBoolean("CoverTerrain");
        project.ignoreOverlaps = tag.getBoolean("IgnoreOverlaps");
        project.startLayer = Math.max(0, tag.getInt("StartLayer"));
        project.autoCraftMaterials = tag.getBoolean("AutoCraftMaterials");
        project.siteConfirmed = tag.getBoolean("SiteConfirmed");
        project.placementYaw = com.touhoumaid.construction.placement.SchematicYaw.normalize(
                tag.getInt("PlacementYaw"));
        project.conflictCount = tag.getInt("ConflictCount");
        project.totalBlocks = tag.getInt("TotalBlocks");
        project.placedBlocks = tag.getInt("PlacedBlocks");
        project.consumedMaterials = tag.getInt("ConsumedMaterials");
        if (tag.contains("PlacedByBlockId", Tag.TAG_LIST)) {
            for (Tag placedTag : tag.getList("PlacedByBlockId", Tag.TAG_COMPOUND)) {
                if (placedTag instanceof CompoundTag compound) {
                    String blockId = compound.getString("BlockId");
                    int count = compound.getInt("Count");
                    if (!blockId.isEmpty() && count > 0) {
                        project.placedByBlockId.put(blockId, count);
                    }
                }
            }
        }
        if (tag.contains("AssignedMaids", Tag.TAG_LIST)) {
            for (Tag maidTag : tag.getList("AssignedMaids", Tag.TAG_COMPOUND)) {
                if (maidTag instanceof CompoundTag compound && compound.hasUUID("Id")) {
                    project.assignedMaids.add(compound.getUUID("Id"));
                }
            }
        }
        if (tag.getBoolean("HasAnchor")) {
            project.anchor = new BlockPos(tag.getInt("Ax"), tag.getInt("Ay"), tag.getInt("Az"));
        }
        try {
            project.status = Status.valueOf(tag.getString("Status"));
        } catch (IllegalArgumentException ignored) {
            project.status = Status.DRAFT;
        }
        if (tag.getBoolean("HasBuildSession") && tag.hasUUID("BuildOwnerId")) {
            project.buildOwnerId = tag.getUUID("BuildOwnerId");
            project.buildItemMode = tag.getBoolean("BuildItemMode");
            try {
                project.buildHand = InteractionHand.valueOf(tag.getString("BuildHand"));
            } catch (IllegalArgumentException ignored) {
                project.buildHand = InteractionHand.MAIN_HAND;
            }
            project.buildTerminalPos = new BlockPos(
                    tag.getInt("BuildTx"), tag.getInt("BuildTy"), tag.getInt("BuildTz"));
        }
        if (tag.contains("IgnoredBlocks", Tag.TAG_LIST)) {
            for (Tag ignoredTag : tag.getList("IgnoredBlocks", Tag.TAG_STRING)) {
                project.ignoredBlockIds.add(ignoredTag.getAsString());
            }
        }
        if (tag.contains("MissingBlockReplacements", Tag.TAG_LIST)) {
            for (Tag rowTag : tag.getList("MissingBlockReplacements", Tag.TAG_COMPOUND)) {
                if (rowTag instanceof CompoundTag compound) {
                    String missing = compound.getString("Missing");
                    String replacement = compound.getString("Replacement");
                    if (!missing.isEmpty() && !replacement.isEmpty()) {
                        project.missingBlockReplacements.put(missing, replacement);
                    }
                }
            }
        }
        if (tag.contains("PlacementFailures", Tag.TAG_LIST)) {
            for (Tag failureTag : tag.getList("PlacementFailures", Tag.TAG_STRING)) {
                project.placementFailures.add(failureTag.getAsString());
            }
        }
        return project;
    }

    public static ListTag saveList(List<BuildProject> projects) {
        ListTag list = new ListTag();
        for (BuildProject project : projects) {
            list.add(project.save());
        }
        return list;
    }

    public static List<BuildProject> loadList(ListTag list) {
        List<BuildProject> projects = new ArrayList<>();
        for (Tag tag : list) {
            if (tag instanceof CompoundTag compound) {
                projects.add(load(compound));
            }
        }
        return projects;
    }

    public static Optional<BuildProject> find(List<BuildProject> projects, String id) {
        if (id == null || id.isEmpty()) {
            return Optional.empty();
        }
        return projects.stream().filter(p -> id.equals(p.id())).findFirst();
    }
}
