package com.smartlogix.order.dto;

import java.math.BigDecimal;

public record OrderLineResponse(
        String sku,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineAmount
) {
}
