package com.touhoumaid.construction.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/** Per-project Hub UI state for the current client game session (cleared on world unload). */
@OnlyIn(Dist.CLIENT)
public final class HubSessionCache {
    private static final Map<String, Integer> PAGE = new HashMap<>();
    private static final Map<String, Integer> MATERIAL_SCROLL = new HashMap<>();
    private static final Map<String, Integer> MAID_SCROLL = new HashMap<>();

    private HubSessionCache() {
    }

    public static int page(String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            return 1;
        }
        return PAGE.getOrDefault(projectId, 1);
    }

    public static void setPage(String projectId, int page) {
        if (projectId == null || projectId.isEmpty()) {
            return;
        }
        PAGE.put(projectId, page);
    }

    public static int materialScroll(String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            return 0;
        }
        return MATERIAL_SCROLL.getOrDefault(projectId, 0);
    }

    public static void setMaterialScroll(String projectId, int scroll) {
        if (projectId == null || projectId.isEmpty()) {
            return;
        }
        MATERIAL_SCROLL.put(projectId, scroll);
    }

    public static int maidScroll(String projectId) {
        if (projectId == null || projectId.isEmpty()) {
            return 0;
        }
        return MAID_SCROLL.getOrDefault(projectId, 0);
    }

    public static void setMaidScroll(String projectId, int scroll) {
        if (projectId == null || projectId.isEmpty()) {
            return;
        }
        MAID_SCROLL.put(projectId, scroll);
    }
}
