package com.smartlogix.order.service;

<<<<<<< HEAD
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.resources.preference.Preference;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
=======
import com.smartlogix.order.client.*;
import com.smartlogix.order.domain.*;
import com.smartlogix.order.dto.*;
import com.smartlogix.order.exception.*;
import com.smartlogix.order.repository.PurchaseOrderRepository;
>>>>>>> 46c7468a155f9b5b4b4e2143b720421e3a15402c
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final PurchaseOrderRepository repository;
    private final InventoryClient inventoryClient;
    private final ShipmentClient shipmentClient;

   
    @Value("${mercadopago.access-token}") --> se pasa el token 
    private String mercadoPagoToken;

    public OrderService(
            PurchaseOrderRepository repository,
            InventoryClient inventoryClient,
            ShipmentClient shipmentClient
    ) {
=======
    public OrderService(PurchaseOrderRepository repository, InventoryClient inventoryClient, ShipmentClient shipmentClient) {
>>>>>>> 46c7468a155f9b5b4b4e2143b720421e3a15402c
        this.repository = repository;
        this.inventoryClient = inventoryClient;
        this.shipmentClient = shipmentClient;
    }

<<<<<<< HEAD
    public OrderResponse createOrder(CreateOrderRequest request) {
        PurchaseOrder order = buildOrder(request);
        repository.save(order);

        // 1. Verificamos disponibilidad de inventario
        for (OrderLine line : order.getLines()) {
            InventoryAvailabilityResponse availability = inventoryClient.checkAvailability(line.getSku(), line.getQuantity());
            if (availability == null || !availability.available()) {
                order.setStatus(OrderStatus.REJECTED);
                order.setRejectionReason("Stock insuficiente para SKU " + line.getSku());
                repository.save(order);
                return toResponse(order, null); // Pasamos null si falla
            }
        }

        // 2. Reservamos inventario
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
                return toResponse(order, null); // Pasamos null si falla
            }
        }

        order.setStatus(OrderStatus.APPROVED);
        repository.save(order);

        // Pasarela simulada: el "link de pago" apunta al microservicio dedicado
        // payment-service, que orquesta el checkout y notifica el resultado aquí.
        String paymentUrl = gatewayBaseUrl + "/api/payments/" + order.getOrderNumber() + "/checkout";

        return toResponse(order, paymentUrl);
    }

    // Llamado por payment-service (microservicio dedicado a la pasarela) para
    // aplicar el resultado del pago: marca PAID o libera el stock reservado y
    // marca FAILED. No decide redirecciones de frontend; eso lo resuelve
    // payment-service según el status devuelto aquí.
    public OrderResponse applyPaymentConfirmation(String orderNumber, boolean approved) {
        PurchaseOrder order = repository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException("No existe la orden " + orderNumber));

        if (order.getStatus() != OrderStatus.APPROVED) {
            return toResponse(order, null);
        }

        if (approved) {
            order.setStatus(OrderStatus.PAID);
            repository.save(order);
            return toResponse(order, null);
        }

        releaseReservedLines(order.getLines());
        order.setStatus(OrderStatus.FAILED);
        order.setRejectionReason("Pago rechazado (simulado)");
        repository.save(order);
        return toResponse(order, null);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders() {
        return repository.findAll().stream()
                .map(order -> toResponse(order, null))
                .toList();
=======
    // --- MÉTODOS QUE EL CONTROLADOR NECESITA ---

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders() {
        return repository.findAll().stream().map(this::toResponse).toList();
>>>>>>> 46c7468a155f9b5b4b4e2143b720421e3a15402c
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(String orderNumber) {
        return repository.findByOrderNumber(orderNumber)
                .map(this::toResponse)
                .orElseThrow(() -> new OrderNotFoundException("No existe la orden " + orderNumber));
<<<<<<< HEAD
        return toResponse(order, null);
=======
>>>>>>> 46c7468a155f9b5b4b4e2143b720421e3a15402c
    }

    public OrderResponse updateOrderStatus(String orderNumber, String statusString) {
        PurchaseOrder order = repository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException("No existe la orden " + orderNumber));
<<<<<<< HEAD

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

        return toResponse(repository.save(order), null);
    }

    public OrderResponse updateOrder(String orderNumber, CreateOrderRequest request) {
        PurchaseOrder existingOrder = repository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException("No existe la orden " + orderNumber));

        existingOrder.setCustomerName(request.customerName().trim());
        existingOrder.setCustomerEmail(request.customerEmail().trim().toLowerCase());
        existingOrder.setShippingAddress(request.shippingAddress().trim());
        
        existingOrder.getLines().clear();
        for (OrderLineRequest lineRequest : request.lines()) {
            OrderLine line = new OrderLine();
            line.setSku(lineRequest.sku().trim().toUpperCase());
            line.setProductName(lineRequest.productName() != null ? lineRequest.productName().trim() : null);
            line.setQuantity(lineRequest.quantity());
            line.setUnitPrice(lineRequest.unitPrice());
            existingOrder.addLine(line);
        }
        
        existingOrder.setTotalAmount(calculateTotal(request.lines()));

        repository.save(existingOrder);
        return toResponse(existingOrder, null);
=======
        
        OrderStatus nextStatus = OrderStatus.valueOf(statusString.toUpperCase());
        order.setStatus(nextStatus);
        return toResponse(repository.save(order));
>>>>>>> 46c7468a155f9b5b4b4e2143b720421e3a15402c
    }

    public void deleteOrder(String orderNumber) {
        PurchaseOrder order = repository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException("No existe la orden " + orderNumber));
        repository.delete(order);
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        PurchaseOrder order = buildOrder(request);
        // ... (Tu lógica de inventario aquí)
        order.setStatus(OrderStatus.APPROVED);
        return toResponse(repository.save(order));
    }

    public OrderResponse updateOrder(String orderNumber, CreateOrderRequest request) {
        PurchaseOrder order = repository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException("No existe la orden " + orderNumber));
        
        order.setCustomerName(request.customerName());
        order.setTotalAmount(calculateTotalWithDiscounts(request));
        return toResponse(repository.save(order));
    }

    // --- LÓGICA DE DESCUENTOS CENTRALIZADA ---
    private BigDecimal calculateTotalWithDiscounts(CreateOrderRequest request) {
        BigDecimal baseSubtotal = request.lines().stream()
                .map(l -> l.unitPrice().multiply(BigDecimal.valueOf(l.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discount = BigDecimal.ZERO;
        String email = request.customerEmail().trim().toLowerCase();
        String code = request.discountCode() != null ? request.discountCode().toUpperCase() : "";

        if ("2X1".equals(code)) {
            discount = request.lines().stream()
                .filter(l -> l.quantity() >= 2)
                .map(l -> l.unitPrice())
                .min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        }

        BigDecimal total = baseSubtotal.subtract(discount);
        if (email.endsWith("@duocuc.cl")) {
            total = total.multiply(new BigDecimal("0.75"));
        }
        return total;
    }

    private PurchaseOrder buildOrder(CreateOrderRequest request) {
        PurchaseOrder order = new PurchaseOrder();
<<<<<<< HEAD
        
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        order.setUsername(currentUsername);
        
        order.setCustomerName(request.customerName().trim());
        order.setCustomerEmail(request.customerEmail().trim().toLowerCase());
        order.setShippingAddress(request.shippingAddress().trim());
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(calculateTotal(request.lines()));

        for (OrderLineRequest lineRequest : request.lines()) {
            OrderLine line = new OrderLine();
            line.setSku(lineRequest.sku().trim().toUpperCase());
            line.setProductName(lineRequest.productName() != null ? lineRequest.productName().trim() : null);
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
            } catch (Exception ex) {
                log.error("No fue posible liberar el stock reservado para SKU {} (cantidad {})",
                        line.getSku(), line.getQuantity(), ex);
            }
        }
    }

    private OrderResponse toResponse(PurchaseOrder order, String paymentUrl) {
        List<OrderLineResponse> lines = order.getLines().stream()
                .map(line -> new OrderLineResponse(
                        line.getSku(),
                        line.getProductName(),
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
                lines,
                paymentUrl 
        );
=======
        order.setCustomerName(request.customerName());
        order.setCustomerEmail(request.customerEmail());
        order.setTotalAmount(calculateTotalWithDiscounts(request));
        order.setDiscountCode(request.discountCode());
        order.setStatus(OrderStatus.PENDING);
        return order;
    }

    private OrderResponse toResponse(PurchaseOrder o) {
        return new OrderResponse(o.getOrderNumber(), o.getUsername(), o.getCustomerName(), 
                                 o.getCustomerEmail(), o.getShippingAddress(), o.getStatus(), 
                                 o.getTotalAmount(), o.getTrackingCode(), o.getRejectionReason(), 
                                 o.getCreatedAt(), new ArrayList<>());
>>>>>>> 46c7468a155f9b5b4b4e2143b720421e3a15402c
    }
}   