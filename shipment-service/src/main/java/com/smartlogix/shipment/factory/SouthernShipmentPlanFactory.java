package com.smartlogix.shipment.factory;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class SouthernShipmentPlanFactory extends ShipmentPlanFactory {

    @Override
    public boolean supports(String normalizedAddress) {
        return normalizedAddress.contains("sur") || normalizedAddress.contains("magallanes");
    }

    @Override
    public ShipmentPlan createPlan(String normalizedAddress) {
        return new ShipmentPlan(
                pickCarrier(normalizedAddress, "Starken", "BlueExpress"),
                buildRouteCode(normalizedAddress, "SUR"),
                5
        );
    }
}
