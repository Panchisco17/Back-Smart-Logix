package com.smartlogix.order.service;

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {

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
        this.repository = repository;
        this.inventoryClient = inventoryClient;
        this.shipmentClient = shipmentClient;
    }

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

       
        MercadoPagoConfig.setAccessToken(mercadoPagoToken); --> se generan los link de mercadopago 
        String initPointUrl = null;

        try {
            List<PreferenceItemRequest> items = new ArrayList<>();
            PreferenceItemRequest item = PreferenceItemRequest.builder()
                    .title("Pedido Mil Sabores - Orden " + order.getOrderNumber())
                    .quantity(1)
                    .unitPrice(order.getTotalAmount()) // Usamos el total real de tu carrito
                    .currencyId("CLP")
                    .build();
            items.add(item);

            PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                    .success("http://localhost:5173/mis-pedidos") 
                    .failure("http://localhost:5173/carrito")     
                    .pending("http://localhost:5173/mis-pedidos") 
                    .build();

            PreferenceRequest preferenceRequest = PreferenceRequest.builder()
                    .items(items)
                    .backUrls(backUrls)
                    .autoReturn("approved") 
                    .build();

            PreferenceClient client = new PreferenceClient();
            Preference preference = client.create(preferenceRequest);
            
            initPointUrl = preference.getInitPoint(); // Obtenemos el link de cobro

        } catch (Exception e) {
            System.err.println("Error al generar el link de Mercado Pago: " + e.getMessage());
        }

        // Retornamos la orden aprobada junto con la URL de pago
        return toResponse(order, initPointUrl);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> getOrders() {
        return repository.findAll().stream()
                .map(order -> toResponse(order, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderByNumber(String orderNumber) {
        PurchaseOrder order = repository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new OrderNotFoundException("No existe la orden " + orderNumber));
        return toResponse(order, null);
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
            line.setQuantity(lineRequest.quantity());
            line.setUnitPrice(lineRequest.unitPrice());
            existingOrder.addLine(line);
        }
        
        existingOrder.setTotalAmount(calculateTotal(request.lines()));

        repository.save(existingOrder);
        return toResponse(existingOrder, null);
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

    --aqui esta recibiendo el paymentUrl
    private OrderResponse toResponse(PurchaseOrder order, String paymentUrl) {
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
                lines,
                paymentUrl 
        );
    }
}   