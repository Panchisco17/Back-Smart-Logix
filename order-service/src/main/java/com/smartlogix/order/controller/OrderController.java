package com.smartlogix.order.controller;

import com.smartlogix.order.dto.CreateOrderRequest;
import com.smartlogix.order.dto.OrderResponse;
import com.smartlogix.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam; 
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping
    public List<OrderResponse> listOrders() {
        return orderService.getOrders();
    }

    @GetMapping("/{orderNumber}")
    public OrderResponse findByOrderNumber(@PathVariable String orderNumber) {
        return orderService.getOrderByNumber(orderNumber);
    }

    @PutMapping("/{orderNumber}/status")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_WAREHOUSE_MANAGER')")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable String orderNumber,
            @RequestParam String status) {
        return ResponseEntity.ok(orderService.updateOrderStatus(orderNumber, status));
    }

    @PutMapping("/{orderNumber}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public OrderResponse updateOrder(
            @PathVariable String orderNumber,
            @Valid @RequestBody CreateOrderRequest request) {
        return orderService.updateOrder(orderNumber, request);
    }

    @DeleteMapping("/{orderNumber}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteOrder(@PathVariable String orderNumber) {
        orderService.deleteOrder(orderNumber);
        return ResponseEntity.noContent().build();
    }

    // Endpoint interno: lo invoca payment-service (no el navegador) para aplicar
    // el resultado del pago una vez que el cliente aprueba o rechaza en la pasarela.
    // Restringido a ROLE_ADMIN porque payment-service siempre llama con su token
    // de servicio (ese rol) — así un usuario normal no puede marcar su propia
    // orden como pagada llamando a este endpoint directamente.
    @PutMapping("/{orderNumber}/payment-confirmation")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public OrderResponse applyPaymentConfirmation(
            @PathVariable String orderNumber,
            @RequestParam boolean approved) {
        return orderService.applyPaymentConfirmation(orderNumber, approved);
    }
}