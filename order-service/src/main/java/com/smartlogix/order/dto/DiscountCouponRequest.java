package com.smartlogix.order.dto;

import com.smartlogix.order.domain.DiscountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DiscountCouponRequest(
        @NotBlank String code,
        String description,
        @NotNull DiscountType type,
        BigDecimal value,
        BigDecimal minSubtotal,
        String requiredEmailDomain,
        boolean firstPurchaseOnly,
        boolean active,
        OffsetDateTime startDate,
        OffsetDateTime endDate
) {
}
