package com.touhoumaid.construction.blueprint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Registry resolve report: ids present in the blueprint but missing from the current game.
 */
public final class BlueprintLoadReport {
    private final List<String> missingBlocks = new ArrayList<>();
    private final List<String> missingEntityTypes = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();

    public void addMissingBlock(String id) {
        if (id != null && !id.isEmpty()) {
            missingBlocks.add(id);
        }
    }

    public void addMissingEntityType(String id) {
        if (id != null && !id.isEmpty()) {
            missingEntityTypes.add(id);
        }
    }

    public void addNote(String note) {
        if (note != null && !note.isEmpty()) {
            notes.add(note);
        }
    }

    public List<String> missingBlocks() {
        return Collections.unmodifiableList(dedupe(missingBlocks));
    }

    public List<String> missingEntityTypes() {
        return Collections.unmodifiableList(dedupe(missingEntityTypes));
    }

    public List<String> notes() {
        return Collections.unmodifiableList(notes);
    }

    public boolean hasMissing() {
        return !missingBlocks().isEmpty() || !missingEntityTypes().isEmpty();
    }

    private static List<String> dedupe(List<String> input) {
        Set<String> set = new LinkedHashSet<>(input);
        return new ArrayList<>(set);
    }
}
