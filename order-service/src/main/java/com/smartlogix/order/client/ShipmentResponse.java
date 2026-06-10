package com.smartlogix.order.client;

import java.time.LocalDate;

public record ShipmentResponse(
        String trackingCode,
        String orderNumber,
        String carrier,
        String routeCode,
        LocalDate estimatedDeliveryDate,
        String status
) {
}
