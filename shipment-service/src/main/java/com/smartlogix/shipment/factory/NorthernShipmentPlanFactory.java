package com.smartlogix.shipment.factory;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class NorthernShipmentPlanFactory extends ShipmentPlanFactory {

    @Override
    public boolean supports(String normalizedAddress) {
        return normalizedAddress.contains("norte") || normalizedAddress.contains("arica");
    }

    @Override
    public ShipmentPlan createPlan(String normalizedAddress) {
        return new ShipmentPlan(
                pickCarrier(normalizedAddress, "Chilexpress", "BlueExpress"),
                buildRouteCode(normalizedAddress, "NORTE"),
                4
        );
    }
}
