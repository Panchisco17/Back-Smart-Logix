package com.smartlogix.order.client;

public record InventoryAvailabilityResponse(
        String sku,
        int requestedQuantity,
        int availableQuantity,
        boolean available
) {
}
