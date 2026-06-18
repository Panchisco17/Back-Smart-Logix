package com.smartlogix.order.client;

import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class ShipmentClient {

    private final RestTemplate restTemplate;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public ShipmentClient(RestTemplate restTemplate, CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.restTemplate = restTemplate;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public ShipmentResponse requestShipment(ShipmentRequest request) {
        String token = "";
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            token = attributes.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
        }
        
        final String finalToken = token;

        return circuitBreakerFactory.create("shipmentService").run(
                () -> {
                    HttpHeaders headers = new HttpHeaders();
                    if (finalToken != null && !finalToken.isEmpty()) {
                        headers.set(HttpHeaders.AUTHORIZATION, finalToken);
                    }
                    headers.set("Content-Type", "application/json");

                    HttpEntity<ShipmentRequest> entity = new HttpEntity<>(request, headers);

                    ResponseEntity<ShipmentResponse> response = restTemplate.exchange(
                            "http://shipment-service/api/shipments",
                            HttpMethod.POST,
                            entity,
                            ShipmentResponse.class
                    );
                    
                    return response.getBody();
                },
                throwable -> {
                    System.err.println("🚨 ERROR REAL AL LLAMAR A SHIPMENT-SERVICE 🚨: " + throwable.getMessage());
                    return fallbackResponse(request);
                }
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