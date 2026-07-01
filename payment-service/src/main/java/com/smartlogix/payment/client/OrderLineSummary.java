package com.smartlogix.payment.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderLineSummary(
        String sku,
        String productName,
        int quantity,
        BigDecimal unitPrice
) {
}
