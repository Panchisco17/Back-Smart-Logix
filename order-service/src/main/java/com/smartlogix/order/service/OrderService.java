package com.smartlogix.order.service;

import com.smartlogix.order.client.InventoryAvailabilityResponse;
import com.smartlogix.order.client.InventoryClient;
import com.smartlogix.order.client.InventoryClientException;
import com.smartlogix.order.client.ShipmentClient;
import com.smartlogix.order.client.ShipmentRequest;
import com.smartlogix.order.client.ShipmentResponse;
import com.smartlogix.order.domain.OrderLine;
import com.smartlogix.order.domain.OrderStatus;
import com.smartlogix.order.domain.PurchaseOrder;
import com.smartlogix.order.dto.CreateOrderRequest;
import com.smartlogix.order.dto.OrderLineRequest;
import com.smartlogix.order.dto.OrderLineResponse;
import com.smartlogix.order.dto.OrderResponse;
import com.smartlogix.order.exception.OrderNotFoundException;
import com.smartlogix.order.repository.PurchaseOrderRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {

    private final PurchaseOrderRepository repository;
    private final InventoryClient inventoryClient;
    private final ShipmentClient shipmentClient;

    public OrderService(
            PurchaseOrderRepository repository,
            InventoryClient inventoryClient,
            ShipmentClient shipmentClient
    ) {
        this.repository = repository;
        this.inventoryClient = inventoryClient;
        this.shipmentClient = shipmentClient;
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        PurchaseOrder order = buildOrder(request);
        repository.save(order);

        for (OrderLine line : order.getLines()) {
            InventoryAvailabilityResponse availability = inventoryClient.checkAvailability(line.getSku(), line.getQuantity());
            if (availability == null || !availability.available()) {
                order.setStatus(OrderStatus.REJECTED);
                order.setRejectionReason("Stock insuficiente para SKU " + line.getSku());
                repository.save(order);
                return toResponse(order);
            }
        }

        List<OrderLine> reservedLines = new ArrayList<>();
        for (OrderLine line : order.getLines()) {
            try {
                inventoryClient.reserve(line.getSku(), line.getQuantity());
                reservedLines.add(line);
            } catch (InventoryClientException ex) {
                releaseReservedLines(reservedLines);
                order.setStatus(OrderStatus.REJECTED);
                order.setRejectionReason("No fue posible reservar inventario. " + ex.getMessage());
                repository.save(order);
                return toResponse(order);
            }
        }

        order.setStatus(OrderStatus.APPROVED);

        ShipmentResponse shipmentResponse = shipmentClient.requestShipment(
                new ShipmentRequest(order.getOrderNumber(), order.getShippingAddress(), totalUnits(order))
        );

        if (shipmentResponse == null || shipmentResponse.trackingCode() == null) {
            order.setStatus(OrderStatus.FAILED);
            order.setRejectionReason("Servicio de envios no disponible. Asignacion manual requerida.");
            repository.save(order);
            return toResponse(order);
        }

        order.setStatus(OrderStatus.SHIPMENT_REQUESTED);
        order.setTrackingCode(shipmentResponse.trackingCode());
        repository.save(order);

        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(String orderNumber) {
        PurchaseOrder order = repository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException("No existe la orden " + orderNumber));
        return toResponse(order);
    }

    // --- NUEVO MÉTODO PARA ACTUALIZAR (PUT) ---
    public OrderResponse updateOrder(String orderNumber, CreateOrderRequest request) {
        PurchaseOrder existingOrder = repository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException("No existe la orden " + orderNumber));

        // Actualizamos los datos del cliente
        existingOrder.setCustomerName(request.customerName().trim());
        existingOrder.setCustomerEmail(request.customerEmail().trim().toLowerCase());
        existingOrder.setShippingAddress(request.shippingAddress().trim());
        
        // Limpiamos las líneas anteriores y agregamos las nuevas
        existingOrder.getLines().clear();
        for (OrderLineRequest lineRequest : request.lines()) {
            OrderLine line = new OrderLine();
            line.setSku(lineRequest.sku().trim().toUpperCase());
            line.setQuantity(lineRequest.quantity());
            line.setUnitPrice(lineRequest.unitPrice());
            existingOrder.addLine(line);
        }
        
        existingOrder.setTotalAmount(calculateTotal(request.lines()));

        repository.save(existingOrder);
        return toResponse(existingOrder);
    }

    // --- NUEVO MÉTODO PARA ELIMINAR (DELETE) ---
    public void deleteOrder(String orderNumber) {
        PurchaseOrder order = repository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException("No existe la orden " + orderNumber));
        
        // Si hay lógica adicional como liberar inventario antes de borrar, iría aquí.
        // Por ahora, procedemos a borrar la orden de la base de datos.
        repository.delete(order);
    }

    private PurchaseOrder buildOrder(CreateOrderRequest request) {
        PurchaseOrder order = new PurchaseOrder();
        order.setCustomerName(request.customerName().trim());
        order.setCustomerEmail(request.customerEmail().trim().toLowerCase());
        order.setShippingAddress(request.shippingAddress().trim());
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(calculateTotal(request.lines()));

        for (OrderLineRequest lineRequest : request.lines()) {
            OrderLine line = new OrderLine();
            line.setSku(lineRequest.sku().trim().toUpperCase());
            line.setQuantity(lineRequest.quantity());
            line.setUnitPrice(lineRequest.unitPrice());
            order.addLine(line);
        }

        return order;
    }

    private BigDecimal calculateTotal(List<OrderLineRequest> lines) {
        return lines.stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int totalUnits(PurchaseOrder order) {
        return order.getLines().stream().mapToInt(OrderLine::getQuantity).sum();
    }

    private void releaseReservedLines(List<OrderLine> reservedLines) {
        for (OrderLine line : reservedLines) {
            try {
                inventoryClient.release(line.getSku(), line.getQuantity());
            } catch (Exception ignored) {
            }
        }
    }

    private OrderResponse toResponse(PurchaseOrder order) {
        List<OrderLineResponse> lines = order.getLines().stream()
                .map(line -> new OrderLineResponse(
                        line.getSku(),
                        line.getQuantity(),
                        line.getUnitPrice(),
                        line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity()))
                ))
                .toList();

        return new OrderResponse(
                order.getOrderNumber(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getTrackingCode(),
                order.getRejectionReason(),
                order.getCreatedAt(),
                lines
        );
    }
}