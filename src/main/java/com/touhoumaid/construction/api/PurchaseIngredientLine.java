package com.touhoumaid.construction.api;

/**
 * One mall-purchasable leaf ingredient needed after recipe expansion.
 */
public record PurchaseIngredientLine(String itemId, int need) {
    public PurchaseIngredientLine {
        itemId = itemId == null ? "" : itemId;
        need = Math.max(0, need);
    }
}
