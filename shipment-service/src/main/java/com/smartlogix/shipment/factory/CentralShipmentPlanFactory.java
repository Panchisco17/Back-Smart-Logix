package com.smartlogix.shipment.factory;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class CentralShipmentPlanFactory extends ShipmentPlanFactory {

    @Override
    public boolean supports(String normalizedAddress) {
        return true;
    }

    @Override
    public ShipmentPlan createPlan(String normalizedAddress) {
        return new ShipmentPlan(
                pickCarrier(normalizedAddress, "Chilexpress", "Starken", "BlueExpress"),
                buildRouteCode(normalizedAddress, "CENTRO"),
                2
        );
    }
}
