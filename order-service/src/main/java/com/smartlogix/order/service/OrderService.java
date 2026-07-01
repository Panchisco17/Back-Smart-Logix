package com.smartlogix.order.service;

import com.smartlogix.order.client.InventoryAvailabilityResponse;
import com.smartlogix.order.client.InventoryClient;
import com.smartlogix.order.client.InventoryClientException;
import com.smartlogix.order.client.ShipmentClient;
import com.smartlogix.order.client.ShipmentRequest;
import com.smartlogix.order.client.ShipmentResponse;
import com.smartlogix.order.domain.DiscountCoupon;
import com.smartlogix.order.domain.OrderLine;
import com.smartlogix.order.domain.OrderStatus;
import com.smartlogix.order.domain.PurchaseOrder;
import com.smartlogix.order.dto.CreateOrderRequest;
import com.smartlogix.order.dto.OrderLineRequest;
import com.smartlogix.order.dto.OrderLineResponse;
import com.smartlogix.order.dto.OrderResponse;
import com.smartlogix.order.exception.OrderNotFoundException;
import com.smartlogix.order.repository.DiscountCouponRepository;
import com.smartlogix.order.repository.PurchaseOrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final PurchaseOrderRepository repository;
    private final InventoryClient inventoryClient;
    private final ShipmentClient shipmentClient;
    private final DiscountCouponRepository discountCouponRepository;

    @Value("${app.gateway.base-url}")
    private String gatewayBaseUrl;

    public OrderService(
            PurchaseOrderRepository repository,
            InventoryClient inventoryClient,
            ShipmentClient shipmentClient,
            DiscountCouponRepository discountCouponRepository
    ) {
        this.repository = repository;
        this.inventoryClient = inventoryClient;
        this.shipmentClient = shipmentClient;
        this.discountCouponRepository = discountCouponRepository;
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
        existingOrder.setDiscountCode(request.discountCode());

        existingOrder.getLines().clear();
        for (OrderLineRequest lineRequest : request.lines()) {
            OrderLine line = new OrderLine();
            line.setSku(lineRequest.sku().trim().toUpperCase());
            line.setProductName(lineRequest.productName() != null ? lineRequest.productName().trim() : null);
            line.setQuantity(lineRequest.quantity());
            line.setUnitPrice(lineRequest.unitPrice());
            existingOrder.addLine(line);
        }

        boolean firstPurchase = repository.countByCustomerEmailIgnoreCaseAndOrderNumberNot(
                existingOrder.getCustomerEmail(), orderNumber) == 0;
        existingOrder.setTotalAmount(calculateTotalWithDiscounts(request, firstPurchase));

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
        order.setDiscountCode(request.discountCode());
        order.setStatus(OrderStatus.PENDING);

        boolean firstPurchase = repository.countByCustomerEmailIgnoreCase(order.getCustomerEmail()) == 0;
        order.setTotalAmount(calculateTotalWithDiscounts(request, firstPurchase));

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

    // Calcula el total aplicando el cupón de descuento (si el código existe, está
    // activo, dentro de su vigencia, y cumple las condiciones propias del cupón).
    // Los cupones se administran desde el panel de admin (DiscountCouponController),
    // no hay tipos de descuento hardcodeados aquí.
    private BigDecimal calculateTotalWithDiscounts(CreateOrderRequest request, boolean firstPurchase) {
        BigDecimal baseSubtotal = request.lines().stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String code = request.discountCode() != null ? request.discountCode().trim().toUpperCase() : "";
        if (code.isEmpty()) {
            return baseSubtotal;
        }

        DiscountCoupon coupon = discountCouponRepository.findByCodeIgnoreCase(code).orElse(null);
        if (coupon == null || !isCouponApplicable(coupon, request, firstPurchase, baseSubtotal)) {
            return baseSubtotal;
        }

        BigDecimal total = applyCoupon(coupon, request, baseSubtotal);
        return total.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : total;
    }

    private boolean isCouponApplicable(
            DiscountCoupon coupon, CreateOrderRequest request, boolean firstPurchase, BigDecimal baseSubtotal) {
        if (!coupon.isActive()) {
            return false;
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (coupon.getStartDate() != null && now.isBefore(coupon.getStartDate())) {
            return false;
        }
        if (coupon.getEndDate() != null && now.isAfter(coupon.getEndDate())) {
            return false;
        }
        if (coupon.getMinSubtotal() != null && baseSubtotal.compareTo(coupon.getMinSubtotal()) < 0) {
            return false;
        }
        if (coupon.getRequiredEmailDomain() != null && !coupon.getRequiredEmailDomain().isBlank()) {
            String email = request.customerEmail().trim().toLowerCase();
            if (!email.endsWith(coupon.getRequiredEmailDomain().toLowerCase())) {
                return false;
            }
        }
        return !coupon.isFirstPurchaseOnly() || firstPurchase;
    }

    private BigDecimal applyCoupon(DiscountCoupon coupon, CreateOrderRequest request, BigDecimal baseSubtotal) {
        return switch (coupon.getType()) {
            case PERCENTAGE -> {
                BigDecimal percentage = coupon.getAmount() != null ? coupon.getAmount() : BigDecimal.ZERO;
                BigDecimal factor = BigDecimal.ONE.subtract(
                        percentage.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
                yield baseSubtotal.multiply(factor);
            }
            case FIXED_AMOUNT -> {
                BigDecimal amount = coupon.getAmount() != null ? coupon.getAmount() : BigDecimal.ZERO;
                yield baseSubtotal.subtract(amount);
            }
            case TWO_FOR_ONE -> {
                BigDecimal cheapestEligibleLine = request.lines().stream()
                        .filter(line -> line.quantity() >= 2)
                        .map(OrderLineRequest::unitPrice)
                        .min(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);
                yield baseSubtotal.subtract(cheapestEligibleLine);
            }
        };
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
    }
}
