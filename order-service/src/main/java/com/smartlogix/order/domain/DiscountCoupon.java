package com.smartlogix.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "discount_coupons")
public class DiscountCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(length = 150)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DiscountType type;

    // Porcentaje (ej. 25 = 25%) o monto fijo en CLP, según el tipo. No aplica para TWO_FOR_ONE.
    // Nota: se llama "amount" y no "value" porque VALUE es palabra reservada en H2.
    @Column(precision = 14, scale = 2)
    private BigDecimal amount;

    // Subtotal mínimo del carrito para que el cupón sea válido (opcional).
    @Column(precision = 14, scale = 2)
    private BigDecimal minSubtotal;

    // Dominio de correo requerido, ej. "@duocuc.cl" (opcional).
    @Column(length = 60)
    private String requiredEmailDomain;

    @Column(nullable = false)
    private boolean firstPurchaseOnly;

    @Column(nullable = false)
    private boolean active;

    // Vigencia opcional. Si ambos son null, el cupón no vence.
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void beforeInsert() {
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public DiscountType getType() {
        return type;
    }

    public void setType(DiscountType type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getMinSubtotal() {
        return minSubtotal;
    }

    public void setMinSubtotal(BigDecimal minSubtotal) {
        this.minSubtotal = minSubtotal;
    }

    public String getRequiredEmailDomain() {
        return requiredEmailDomain;
    }

    public void setRequiredEmailDomain(String requiredEmailDomain) {
        this.requiredEmailDomain = requiredEmailDomain;
    }

    public boolean isFirstPurchaseOnly() {
        return firstPurchaseOnly;
    }

    public void setFirstPurchaseOnly(boolean firstPurchaseOnly) {
        this.firstPurchaseOnly = firstPurchaseOnly;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public OffsetDateTime getStartDate() {
        return startDate;
    }

    public void setStartDate(OffsetDateTime startDate) {
        this.startDate = startDate;
    }

    public OffsetDateTime getEndDate() {
        return endDate;
    }

    public void setEndDate(OffsetDateTime endDate) {
        this.endDate = endDate;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
