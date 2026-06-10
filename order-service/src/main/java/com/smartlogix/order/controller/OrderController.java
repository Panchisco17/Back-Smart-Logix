package com.smartlogix.order.controller;

import com.smartlogix.order.dto.CreateOrderRequest;
import com.smartlogix.order.dto.OrderResponse;
import com.smartlogix.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    // --- MÉTODOS AGREGADOS PARA SOPORTAR EL FRONTEND ---

    @PutMapping("/{orderNumber}")
    public OrderResponse updateOrder(
            @PathVariable String orderNumber, 
            @Valid @RequestBody CreateOrderRequest request) { 
        // Asumiendo que usas el mismo DTO para actualizar, o cámbialo a UpdateOrderRequest si tienes uno
        return orderService.updateOrder(orderNumber, request);
    }

    @DeleteMapping("/{orderNumber}")
    public ResponseEntity<Void> deleteOrder(@PathVariable String orderNumber) {
        orderService.deleteOrder(orderNumber);
        // Retornamos un 204 No Content que es el estándar HTTP para una eliminación exitosa
        return ResponseEntity.noContent().build(); 
    }
}