package com.smartlogix.shipment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateShipmentRequest(
        @NotBlank String orderNumber,
        @NotBlank String destinationAddress,
        @Min(1) int totalUnits
) {
}
