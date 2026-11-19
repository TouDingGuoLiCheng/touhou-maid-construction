package com.touhoumaid.construction.build;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Session debug NDJSON for agent investigation. Remove after fix verified. */
public final class AgentDebugLog {
    private static final String SESSION = "f4bcb3";
    private static final Path LOG = Path.of("d:", "Game", "MineCraft", "模组开发", "debug-f4bcb3.log");
    private static final Gson GSON = new Gson();

    private AgentDebugLog() {
    }

    // #region agent log
    public static void log(String hypothesisId, String location, String message, JsonObject data) {
        JsonObject row = new JsonObject();
        row.addProperty("sessionId", SESSION);
        row.addProperty("hypothesisId", hypothesisId);
        row.addProperty("location", location);
        row.addProperty("message", message);
        row.addProperty("timestamp", System.currentTimeMillis());
        if (data != null) {
            row.add("data", data);
        }
        try {
            Files.writeString(
                    LOG,
                    GSON.toJson(row) + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // best-effort
        }
    }

    public static JsonObject data() {
        return new JsonObject();
    }
    // #endregion
}
