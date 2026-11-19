package com.touhoumaid.construction.api;

/**
 * One schematic material row that is short in the terminal vault.
 * {@link #purchaseItemId()} follows {@link com.touhoumaid.construction.project.MaterialDisplay}
 * (e.g. {@code minecraft:potted_poppy} → {@code minecraft:poppy}).
 */
public record ShortageLine(String blockId, String purchaseItemId, int need) {
    public ShortageLine {
        if (blockId == null) {
            blockId = "";
        }
        if (purchaseItemId == null) {
            purchaseItemId = "";
        }
        if (need < 0) {
            need = 0;
        }
    }
}
