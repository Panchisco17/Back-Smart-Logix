package com.smartlogix.order.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class InventoryClient {

    private final RestTemplate restTemplate;

    public InventoryClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public InventoryAvailabilityResponse checkAvailability(String sku, int quantity) {
        return restTemplate.getForObject(
                "http://inventory-service/api/inventory/items/{sku}/availability?quantity={quantity}",
                InventoryAvailabilityResponse.class,
                sku,
                quantity
        );
    }

    public void reserve(String sku, int quantity) {
        try {
            restTemplate.postForObject(
                    "http://inventory-service/api/inventory/items/{sku}/reserve?quantity={quantity}",
                    null,
                    Object.class,
                    sku,
                    quantity
            );
        } catch (RestClientException ex) {
            throw new InventoryClientException("No fue posible reservar stock para " + sku, ex);
        }
    }

    public void release(String sku, int quantity) {
        try {
            restTemplate.postForObject(
                    "http://inventory-service/api/inventory/items/{sku}/release?quantity={quantity}",
                    null,
                    Object.class,
                    sku,
                    quantity
            );
        } catch (RestClientException ex) {
            throw new InventoryClientException("No fue posible liberar stock para " + sku, ex);
        }
    }
}
