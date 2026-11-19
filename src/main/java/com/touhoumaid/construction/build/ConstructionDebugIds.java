package com.touhoumaid.construction.build;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

import java.util.UUID;

/** Stable maid labels for construction debug logs (name + short UUID). */
public final class ConstructionDebugIds {
    private ConstructionDebugIds() {
    }

    public static String maid(EntityMaid maid) {
        return maid == null ? "?" : maid(maid.getName().getString(), maid.getUUID());
    }

    public static String maid(String name, UUID id) {
        String safeName = name == null || name.isEmpty() ? "?" : name;
        if (id == null) {
            return safeName + "#????????";
        }
        return safeName + "#" + id.toString().substring(0, 8);
    }
}
