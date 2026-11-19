package com.touhoumaid.construction.blueprint;

import net.minecraft.core.Vec3i;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory schematic loaded from a {@code .litematic} file.
 */
public final class Blueprint {
    private final String sourceName;
    private final int schematicVersion;
    private final int minecraftDataVersion;
    private final String name;
    private final String author;
    private final String description;
    private final long timeCreated;
    private final long timeModified;
    private final int totalBlocksMeta;
    private final int totalVolumeMeta;
    private final Vec3i enclosingSize;
    private final Map<String, BlueprintRegion> regions;

    public Blueprint(
            String sourceName,
            int schematicVersion,
            int minecraftDataVersion,
            String name,
            String author,
            String description,
            long timeCreated,
            long timeModified,
            int totalBlocksMeta,
            int totalVolumeMeta,
            Vec3i enclosingSize,
            Map<String, BlueprintRegion> regions) {
        this.sourceName = sourceName;
        this.schematicVersion = schematicVersion;
        this.minecraftDataVersion = minecraftDataVersion;
        this.name = name == null ? "" : name;
        this.author = author == null ? "" : author;
        this.description = description == null ? "" : description;
        this.timeCreated = timeCreated;
        this.timeModified = timeModified;
        this.totalBlocksMeta = totalBlocksMeta;
        this.totalVolumeMeta = totalVolumeMeta;
        this.enclosingSize = enclosingSize;
        this.regions = Collections.unmodifiableMap(new LinkedHashMap<>(regions));
    }

    public String sourceName() {
        return sourceName;
    }

    public int schematicVersion() {
        return schematicVersion;
    }

    public int minecraftDataVersion() {
        return minecraftDataVersion;
    }

    public String name() {
        return name;
    }

    public String author() {
        return author;
    }

    public String description() {
        return description;
    }

    public long timeCreated() {
        return timeCreated;
    }

    public long timeModified() {
        return timeModified;
    }

    public int totalBlocksMeta() {
        return totalBlocksMeta;
    }

    public int totalVolumeMeta() {
        return totalVolumeMeta;
    }

    public Vec3i enclosingSize() {
        return enclosingSize;
    }

    public Map<String, BlueprintRegion> regions() {
        return regions;
    }

    public Optional<BlueprintRegion> firstRegion() {
        return regions.values().stream().findFirst();
    }

    public int countedNonAirBlocks() {
        int sum = 0;
        for (BlueprintRegion region : regions.values()) {
            sum += region.countNonAir();
        }
        return sum;
    }
}
