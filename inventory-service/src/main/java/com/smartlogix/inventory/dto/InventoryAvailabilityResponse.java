package com.smartlogix.inventory.dto;

public record InventoryAvailabilityResponse(
        String sku,
        int requestedQuantity,
        int availableQuantity,
        boolean available
) {
}
