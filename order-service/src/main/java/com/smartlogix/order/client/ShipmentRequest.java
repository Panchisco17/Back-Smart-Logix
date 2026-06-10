package com.smartlogix.order.client;

public record ShipmentRequest(
        String orderNumber,
        String destinationAddress,
        int totalUnits
) {
}
