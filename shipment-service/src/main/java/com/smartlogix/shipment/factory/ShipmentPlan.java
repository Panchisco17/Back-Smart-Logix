package com.smartlogix.shipment.factory;

public record ShipmentPlan(
        String carrier,
        String routeCode,
        int estimatedDeliveryDays
) {
}
