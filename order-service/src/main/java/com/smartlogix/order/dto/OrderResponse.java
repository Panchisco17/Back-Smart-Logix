package com.smartlogix.order.dto;

import com.smartlogix.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(
        String orderNumber,
        String username,           // <-- Asegúrate de que esto esté aquí
        String customerName,       // <-- Asegúrate de que esto esté aquí
        String customerEmail,      // <-- Asegúrate de que esto esté aquí
        String shippingAddress,    // <-- Asegúrate de que esto esté aquí
        OrderStatus status,
        BigDecimal totalAmount,
        String trackingCode,
        String reason,             
        OffsetDateTime createdAt,
        List<OrderLineResponse> lines
) {
}