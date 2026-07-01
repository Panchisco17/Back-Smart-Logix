package com.smartlogix.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

// order-service devuelve más campos de los que necesitamos (username, email,
// dirección, tracking, etc.); los ignoramos y solo mapeamos lo que usa la pasarela.
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderSummaryResponse(
        String orderNumber,
        String customerName,
        String status,
        BigDecimal totalAmount,
        List<OrderLineSummary> lines
) {
}
