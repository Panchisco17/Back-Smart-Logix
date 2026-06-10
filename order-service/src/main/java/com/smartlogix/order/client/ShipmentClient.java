package com.smartlogix.order.client;

import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class ShipmentClient {

    private final RestTemplate restTemplate;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public ShipmentClient(RestTemplate restTemplate, CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.restTemplate = restTemplate;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public ShipmentResponse requestShipment(ShipmentRequest request) {
        return circuitBreakerFactory.create("shipmentService").run(
                () -> restTemplate.postForObject(
                        "http://shipment-service/api/shipments",
                        request,
                        ShipmentResponse.class
                ),
                throwable -> fallbackResponse(request)
        );
    }

    private ShipmentResponse fallbackResponse(ShipmentRequest request) {
        return new ShipmentResponse(
                null,
                request.orderNumber(),
                "NO_CARRIER",
                "NO_ROUTE",
                null,
                "PENDING_MANUAL_ASSIGNMENT"
        );
    }
}
