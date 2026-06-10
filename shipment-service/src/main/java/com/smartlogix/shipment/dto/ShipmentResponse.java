package com.smartlogix.shipment.dto;

import com.smartlogix.shipment.domain.ShipmentStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ShipmentResponse(
        String trackingCode,
        String orderNumber,
        String carrier,
        String routeCode,
        LocalDate estimatedDeliveryDate,
        ShipmentStatus status,
        OffsetDateTime createdAt
) {
}
