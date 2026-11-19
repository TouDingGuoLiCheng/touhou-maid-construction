package com.touhoumaid.construction.build;

/**
 * Pure priority rule: vault first, then maid backpack, else missing.
 * Kept free of Minecraft types for JVM unit tests.
 */
public final class ScaffoldMaterialPolicy {
    public enum Result {
        FROM_VAULT,
        FROM_MAID,
        MISSING
    }

    private ScaffoldMaterialPolicy() {
    }

    public static Result resolveSource(boolean vaultHas, int maidCount) {
        if (vaultHas) {
            return Result.FROM_VAULT;
        }
        if (maidCount > 0) {
            return Result.FROM_MAID;
        }
        return Result.MISSING;
    }
}
