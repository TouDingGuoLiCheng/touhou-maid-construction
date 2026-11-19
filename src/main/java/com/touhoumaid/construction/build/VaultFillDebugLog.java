package com.touhoumaid.construction.build;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

/** NDJSON debug for creative vault fill / overwrite investigation. Remove after verified. */
public final class VaultFillDebugLog {
    private static final String SESSION = "27cec4";
    private static final Path LOG = Path.of("d:", "Game", "MineCraft", "模组开发", "debug-27cec4.log");
    private static final Gson GSON = new Gson();

    private VaultFillDebugLog() {
    }

    // #region agent log
    public static void log(String hypothesisId, String location, String message, JsonObject data) {
        if (!ConstructionDebug.enabled()) {
            return;
        }
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

    public static void vaultSnapshot(JsonObject data, String key, Container vault) {
        data.add(key, summarizeVault(vault));
    }

    public static void vaultSnapshot(JsonObject data, String key, NonNullList<ItemStack> items) {
        data.add(key, summarizeVault(items));
    }

    private static JsonObject summarizeVault(Container vault) {
        JsonObject summary = new JsonObject();
        Map<String, Integer> totals = new HashMap<>();
        JsonArray slots = new JsonArray();
        int nonEmpty = 0;
        for (int slot = 0; slot < vault.getContainerSize(); slot++) {
            ItemStack stack = vault.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            nonEmpty++;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            totals.merge(id, stack.getCount(), Integer::sum);
            if (slots.size() < 24) {
                JsonObject entry = new JsonObject();
                entry.addProperty("slot", slot);
                entry.addProperty("item", id);
                entry.addProperty("count", stack.getCount());
                slots.add(entry);
            }
        }
        summary.addProperty("nonEmptySlots", nonEmpty);
        summary.addProperty("distinctTypes", totals.size());
        JsonArray types = new JsonArray();
        totals.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(16)
                .forEach(e -> {
                    JsonObject t = new JsonObject();
                    t.addProperty("item", e.getKey());
                    t.addProperty("count", e.getValue());
                    types.add(t);
                });
        summary.add("types", types);
        summary.add("sampleSlots", slots);
        return summary;
    }

    private static JsonObject summarizeVault(NonNullList<ItemStack> items) {
        net.minecraft.world.SimpleContainer view = new net.minecraft.world.SimpleContainer(items.size());
        for (int i = 0; i < items.size(); i++) {
            view.setItem(i, items.get(i));
        }
        return summarizeVault(view);
    }
    // #endregion
}
