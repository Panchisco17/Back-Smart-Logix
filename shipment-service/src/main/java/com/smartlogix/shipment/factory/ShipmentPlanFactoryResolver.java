package com.smartlogix.shipment.factory;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ShipmentPlanFactoryResolver {

    private final List<ShipmentPlanFactory> planFactories;

    public ShipmentPlanFactoryResolver(List<ShipmentPlanFactory> planFactories) {
        this.planFactories = planFactories;
    }

    public ShipmentPlanFactory resolve(String normalizedAddress) {
        return planFactories.stream()
                .filter(factory -> factory.supports(normalizedAddress))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No existe una fabrica para la direccion indicada."));
    }
}
