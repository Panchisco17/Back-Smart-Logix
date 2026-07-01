package com.smartlogix.order.dto;

import com.smartlogix.order.domain.DiscountType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DiscountCouponResponse(
        Long id,
        String code,
        String description,
        DiscountType type,
        BigDecimal amount,
        BigDecimal minSubtotal,
        String requiredEmailDomain,
        boolean firstPurchaseOnly,
        boolean active,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        OffsetDateTime createdAt
) {
}
