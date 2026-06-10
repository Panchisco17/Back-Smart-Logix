package com.smartlogix.shipment.service;

import com.smartlogix.shipment.domain.Shipment;
import com.smartlogix.shipment.domain.ShipmentStatus;
import com.smartlogix.shipment.dto.CreateShipmentRequest;
import com.smartlogix.shipment.dto.ShipmentResponse;
import com.smartlogix.shipment.exception.ShipmentNotFoundException;
import com.smartlogix.shipment.factory.ShipmentPlan;
import com.smartlogix.shipment.factory.ShipmentPlanFactory;
import com.smartlogix.shipment.factory.ShipmentPlanFactoryResolver;
import com.smartlogix.shipment.repository.ShipmentRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ShipmentService {

    private final ShipmentRepository repository;
    private final ShipmentPlanFactoryResolver planFactoryResolver;

    public ShipmentService(
            ShipmentRepository repository,
            ShipmentPlanFactoryResolver planFactoryResolver
    ) {
        this.repository = repository;
        this.planFactoryResolver = planFactoryResolver;
    }

    public ShipmentResponse createShipment(CreateShipmentRequest request) {
        String destinationAddress = request.destinationAddress().trim();
        String normalizedAddress = destinationAddress.toLowerCase(Locale.ROOT);
        ShipmentPlanFactory planFactory = planFactoryResolver.resolve(normalizedAddress);
        ShipmentPlan shipmentPlan = planFactory.createPlan(normalizedAddress);

        Shipment shipment = new Shipment();
        shipment.setOrderNumber(request.orderNumber().trim().toUpperCase());
        shipment.setDestinationAddress(destinationAddress);
        shipment.setTotalUnits(request.totalUnits());
        shipment.setCarrier(shipmentPlan.carrier());
        shipment.setRouteCode(shipmentPlan.routeCode());
        shipment.setEstimatedDeliveryDate(LocalDate.now().plusDays(shipmentPlan.estimatedDeliveryDays()));
        shipment.setStatus(ShipmentStatus.PLANNED);
        shipment.setTrackingCode("SLX-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase());

        return toResponse(repository.save(shipment));
    }

    @Transactional(readOnly = true)
    public List<ShipmentResponse> getShipments() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ShipmentResponse getByTrackingCode(String trackingCode) {
        Shipment shipment = repository.findByTrackingCode(trackingCode.trim().toUpperCase())
                .orElseThrow(() -> new ShipmentNotFoundException("No existe el envio " + trackingCode));
        return toResponse(shipment);
    }

    public ShipmentResponse updateStatus(String trackingCode, ShipmentStatus status) {
        Shipment shipment = repository.findByTrackingCode(trackingCode.trim().toUpperCase())
                .orElseThrow(() -> new ShipmentNotFoundException("No existe el envio " + trackingCode));
        shipment.setStatus(status);
        return toResponse(repository.save(shipment));
    }

    private ShipmentResponse toResponse(Shipment shipment) {
        return new ShipmentResponse(
                shipment.getTrackingCode(),
                shipment.getOrderNumber(),
                shipment.getCarrier(),
                shipment.getRouteCode(),
                shipment.getEstimatedDeliveryDate(),
                shipment.getStatus(),
                shipment.getCreatedAt()
        );
    }
}
