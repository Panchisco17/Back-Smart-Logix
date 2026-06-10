package com.smartlogix.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateInventoryItemRequest(
        @NotBlank String sku,
        @NotBlank String productName,
        @NotBlank String warehouseCode,
        @Min(0) int initialQuantity,
        @Min(0) int reorderLevel
) {
}
