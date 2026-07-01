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
import org.springframework.security.core.context.SecurityContextHolder;
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

    public OrderResponse updateOrderStatus(String orderNumber, String statusString) {
        PurchaseOrder order = repository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException("No existe la orden " + orderNumber));

        OrderStatus nextStatus;
        try {
            nextStatus = OrderStatus.valueOf(statusString.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Estado no válido: " + statusString);
        }

        if (nextStatus == OrderStatus.SHIPMENT_REQUESTED) {
            ShipmentResponse shipmentResponse = shipmentClient.requestShipment(
                    new ShipmentRequest(order.getOrderNumber(), order.getShippingAddress(), totalUnits(order))
            );

            if (shipmentResponse == null || shipmentResponse.trackingCode() == null) {
                throw new RuntimeException("Servicio de envíos no disponible o falló la creación de la guía.");
            }

            order.setTrackingCode(shipmentResponse.trackingCode());
            order.setStatus(OrderStatus.SHIPMENT_REQUESTED);
        } else {
            order.setStatus(nextStatus);
        }

        return toResponse(repository.save(order));
    }

    public void deleteOrder(String orderNumber) {
        PurchaseOrder order = repository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException("No existe la orden " + orderNumber));
        
        repository.delete(order);
    }

    private PurchaseOrder buildOrder(CreateOrderRequest request) {
        PurchaseOrder order = new PurchaseOrder();
        
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        order.setUsername(currentUsername);
        
        order.setCustomerName(request.customerName().trim());
        String email = request.customerEmail().trim().toLowerCase();
        order.setCustomerEmail(email);
        order.setShippingAddress(request.shippingAddress().trim());
        order.setStatus(OrderStatus.PENDING);
        
        if (request.discountCode() != null) {
            order.setDiscountCode(request.discountCode().trim().toUpperCase());
        }

        // 1. Calculamos el total base (sin descuentos)
        BigDecimal baseSubtotal = calculateTotal(request.lines());
        BigDecimal discountAmount = BigDecimal.ZERO;

        // 2. Aplicamos lógica de descuento 2X1
        if ("2X1".equals(order.getDiscountCode())) {
            BigDecimal itemToDiscount = null;
            for (OrderLineRequest line : request.lines()) {
                if (line.quantity() >= 2) {
                    if (itemToDiscount == null || line.unitPrice().compareTo(itemToDiscount) < 0) {
                        itemToDiscount = line.unitPrice();
                    }
                }
            }
            if (itemToDiscount != null) {
                discountAmount = discountAmount.add(itemToDiscount);
            }
        }

        // Subtotal tras restar el 2x1
        BigDecimal subtotalAfterPromo = baseSubtotal.subtract(discountAmount);

        // 3. Aplicamos lógica de descuento del 25% institucional
        if (email.endsWith("@duocuc.cl")) {
            BigDecimal duocDiscount = subtotalAfterPromo.multiply(new BigDecimal("0.25"));
            subtotalAfterPromo = subtotalAfterPromo.subtract(duocDiscount);
        }

        // Se guarda el total final a cobrar
        order.setTotalAmount(subtotalAfterPromo);

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
                order.getUsername(),         
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getShippingAddress(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getTrackingCode(),
                order.getRejectionReason(),
                order.getCreatedAt(),
                lines
        );
    }
}