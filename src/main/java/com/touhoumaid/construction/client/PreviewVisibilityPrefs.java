package com.touhoumaid.construction.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** File-backed outline preference (client game directory). */
public final class PreviewVisibilityPrefs {
    public static final String FILE_NAME = "touhou_maid_construction_preview.txt";

    private PreviewVisibilityPrefs() {
    }

    public static boolean load(Path file, boolean defaultVisible) {
        if (file == null || !Files.isRegularFile(file)) {
            return defaultVisible;
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8).trim();
            if (raw.equalsIgnoreCase("false") || raw.equals("0") || raw.equalsIgnoreCase("off")) {
                return false;
            }
            if (raw.equalsIgnoreCase("true") || raw.equals("1") || raw.equalsIgnoreCase("on")) {
                return true;
            }
            return defaultVisible;
        } catch (IOException e) {
            return defaultVisible;
        }
    }

    public static void save(Path file, boolean visible) throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, visible ? "true" : "false", StandardCharsets.UTF_8);
    }
}
