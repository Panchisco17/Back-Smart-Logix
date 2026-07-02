package com.smartlogix.order.controller;

import com.smartlogix.order.dto.DiscountCouponRequest;
import com.smartlogix.order.dto.DiscountCouponResponse;
import com.smartlogix.order.service.DiscountCouponService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coupons")
public class DiscountCouponController {

    private final DiscountCouponService discountCouponService;

    public DiscountCouponController(DiscountCouponService discountCouponService) {
        this.discountCouponService = discountCouponService;
    }

    // Sin restricción de rol: el checkout de la tienda (cualquier cliente
    // autenticado) necesita leer los cupones vigentes para validar códigos.
    @GetMapping
    public List<DiscountCouponResponse> listCoupons() {
        return discountCouponService.getCoupons();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public DiscountCouponResponse createCoupon(@Valid @RequestBody DiscountCouponRequest request) {
        return discountCouponService.createCoupon(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public DiscountCouponResponse updateCoupon(
            @PathVariable Long id,
            @Valid @RequestBody DiscountCouponRequest request) {
        return discountCouponService.updateCoupon(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public DiscountCouponResponse setActive(
            @PathVariable Long id,
            @RequestParam boolean active) {
        return discountCouponService.setActive(id, active);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deleteCoupon(@PathVariable Long id) {
        discountCouponService.deleteCoupon(id);
        return ResponseEntity.noContent().build();
    }
}
