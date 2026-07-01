package com.smartlogix.payment.client;

import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class OrderClient {

    private final RestTemplate restTemplate;

    public OrderClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public OrderSummaryResponse getOrder(String orderNumber) {
        try {
            return restTemplate.getForObject(
                    "http://order-service/api/orders/{orderNumber}",
                    OrderSummaryResponse.class,
                    orderNumber
            );
        } catch (RestClientException ex) {
            throw new OrderClientException("No fue posible obtener la orden " + orderNumber, ex);
        }
    }

    public OrderSummaryResponse confirmPayment(String orderNumber, boolean approved) {
        try {
            ResponseEntity<OrderSummaryResponse> response = restTemplate.exchange(
                    "http://order-service/api/orders/{orderNumber}/payment-confirmation?approved={approved}",
                    HttpMethod.PUT,
                    null,
                    OrderSummaryResponse.class,
                    orderNumber,
                    approved
            );
            return response.getBody();
        } catch (RestClientException ex) {
            throw new OrderClientException("No fue posible confirmar el pago de la orden " + orderNumber, ex);
        }
    }
}
