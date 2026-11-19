package com.touhoumaid.construction.build;

/** Terminal vault access for per-block material deduction. */
public interface MaterialSource {
    boolean hasMaterial(String blockId);

    boolean tryConsume(String blockId);

    void markDirty();
}
