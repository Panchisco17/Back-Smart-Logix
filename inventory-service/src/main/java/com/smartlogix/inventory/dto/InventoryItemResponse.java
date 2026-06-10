package com.smartlogix.inventory.dto;

import java.time.OffsetDateTime;

public record InventoryItemResponse(
        String sku,
        String productName,
        String warehouseCode,
        int availableQuantity,
        int reservedQuantity,
        int reorderLevel,
        OffsetDateTime updatedAt
) {
}
