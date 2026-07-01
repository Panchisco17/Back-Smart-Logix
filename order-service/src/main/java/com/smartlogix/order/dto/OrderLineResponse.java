package com.smartlogix.order.dto;

import java.math.BigDecimal;

public record OrderLineResponse(
        String sku,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineAmount
) {
}
