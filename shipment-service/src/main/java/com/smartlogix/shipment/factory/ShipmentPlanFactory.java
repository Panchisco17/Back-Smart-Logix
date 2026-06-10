package com.smartlogix.shipment.factory;

public abstract class ShipmentPlanFactory {

    public abstract boolean supports(String normalizedAddress);

    public abstract ShipmentPlan createPlan(String normalizedAddress);

    protected String pickCarrier(String normalizedAddress, String... carriers) {
        int score = Math.abs(normalizedAddress.hashCode());
        return carriers[score % carriers.length];
    }

    protected String buildRouteCode(String normalizedAddress, String regionPrefix) {
        int suffix = Math.abs(normalizedAddress.hashCode() % 900) + 100;
        return "RUTA-" + regionPrefix + "-" + suffix;
    }
}
