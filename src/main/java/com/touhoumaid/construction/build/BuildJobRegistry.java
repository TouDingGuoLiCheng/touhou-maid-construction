package com.touhoumaid.construction.build;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class BuildJobRegistry {
    private static final Map<BuildJobKey, BuildJob> JOBS = new ConcurrentHashMap<>();

    private BuildJobRegistry() {
    }

    public static void register(BuildJob job) {
        JOBS.put(job.key(), job);
        job.indexAllPlaced();
    }

    public static Optional<BuildJob> get(BuildJobKey key) {
        return Optional.ofNullable(JOBS.get(key));
    }

    public static void remove(BuildJobKey key) {
        BuildPlacementIndex.untrackJob(key);
        JOBS.remove(key);
    }

    public static Map<BuildJobKey, BuildJob> all() {
        return Map.copyOf(JOBS);
    }
}
