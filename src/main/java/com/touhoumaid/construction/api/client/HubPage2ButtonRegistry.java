package com.touhoumaid.construction.api.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Companion mods register extra Hub page-2 (materials) buttons here during client setup.
 */
@OnlyIn(Dist.CLIENT)
public final class HubPage2ButtonRegistry {
    private static final List<Entry> ENTRIES = new CopyOnWriteArrayList<>();

    private HubPage2ButtonRegistry() {
    }

    /**
     * @param id stable id (e.g. {@code construction_duoduo_supply:buy})
     * @param labelKey translation key for the button label
     * @param onClick (terminal, selectedProjectId) — selectedProjectId may be empty
     */
    public static void register(String id, String labelKey, BiConsumer<HubTerminalRef, String> onClick) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(labelKey, "labelKey");
        Objects.requireNonNull(onClick, "onClick");
        ENTRIES.removeIf(e -> e.id().equals(id));
        ENTRIES.add(new Entry(id, labelKey, onClick));
    }

    public static List<Entry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(ENTRIES));
    }

    @OnlyIn(Dist.CLIENT)
    public record Entry(String id, String labelKey, BiConsumer<HubTerminalRef, String> onClick) {
    }
}
