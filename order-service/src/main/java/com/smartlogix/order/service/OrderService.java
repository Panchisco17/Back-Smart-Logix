package com.smartlogix.order.service;

import com.smartlogix.order.client.*;
import com.smartlogix.order.domain.*;
import com.smartlogix.order.dto.*;
import com.smartlogix.order.exception.*;
import com.smartlogix.order.repository.PurchaseOrderRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class OrderService {

    private final PurchaseOrderRepository repository;
    private final InventoryClient inventoryClient;
    private final ShipmentClient shipmentClient;

    public OrderService(PurchaseOrderRepository repository, InventoryClient inventoryClient, ShipmentClient shipmentClient) {
        this.repository = repository;
        this.inventoryClient = inventoryClient;
        this.shipmentClient = shipmentClient;
    }

    // --- MÉTODOS QUE EL CONTROLADOR NECESITA ---

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(String orderNumber) {
        return repository.findByOrderNumber(orderNumber)
                .map(this::toResponse)
                .orElseThrow(() -> new OrderNotFoundException("No existe la orden " + orderNumber));
    }

    public OrderResponse updateOrderStatus(String orderNumber, String statusString) {
        PurchaseOrder order = repository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException("No existe la orden " + orderNumber));
        
        OrderStatus nextStatus = OrderStatus.valueOf(statusString.toUpperCase());
        order.setStatus(nextStatus);
        return toResponse(repository.save(order));
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
    }
}