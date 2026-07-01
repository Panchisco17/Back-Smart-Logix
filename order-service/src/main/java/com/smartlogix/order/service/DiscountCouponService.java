package com.smartlogix.order.service;

import com.smartlogix.order.domain.DiscountCoupon;
import com.smartlogix.order.dto.DiscountCouponRequest;
import com.smartlogix.order.dto.DiscountCouponResponse;
import com.smartlogix.order.exception.DiscountCouponNotFoundException;
import com.smartlogix.order.exception.OrderProcessingException;
import com.smartlogix.order.repository.DiscountCouponRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DiscountCouponService {

    private final DiscountCouponRepository repository;

    public DiscountCouponService(DiscountCouponRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<DiscountCouponResponse> getCoupons() {
        return repository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public DiscountCouponResponse createCoupon(DiscountCouponRequest request) {
        String code = request.code().trim().toUpperCase();
        if (repository.existsByCodeIgnoreCase(code)) {
            throw new OrderProcessingException("Ya existe un cupón con el código " + code);
        }

        DiscountCoupon coupon = new DiscountCoupon();
        coupon.setCode(code);
        applyRequest(coupon, request);

        return toResponse(repository.save(coupon));
    }

    public DiscountCouponResponse updateCoupon(Long id, DiscountCouponRequest request) {
        DiscountCoupon coupon = findOrThrow(id);

        String code = request.code().trim().toUpperCase();
        if (!code.equals(coupon.getCode()) && repository.existsByCodeIgnoreCase(code)) {
            throw new OrderProcessingException("Ya existe un cupón con el código " + code);
        }
        coupon.setCode(code);
        applyRequest(coupon, request);

        return toResponse(repository.save(coupon));
    }

    public DiscountCouponResponse setActive(Long id, boolean active) {
        DiscountCoupon coupon = findOrThrow(id);
        coupon.setActive(active);
        return toResponse(repository.save(coupon));
    }

    public void deleteCoupon(Long id) {
        DiscountCoupon coupon = findOrThrow(id);
        repository.delete(coupon);
    }

    private void applyRequest(DiscountCoupon coupon, DiscountCouponRequest request) {
        coupon.setDescription(request.description() != null ? request.description().trim() : null);
        coupon.setType(request.type());
        coupon.setValue(request.value());
        coupon.setMinSubtotal(request.minSubtotal());
        coupon.setRequiredEmailDomain(
                request.requiredEmailDomain() != null && !request.requiredEmailDomain().isBlank()
                        ? request.requiredEmailDomain().trim().toLowerCase()
                        : null);
        coupon.setFirstPurchaseOnly(request.firstPurchaseOnly());
        coupon.setActive(request.active());
        coupon.setStartDate(request.startDate());
        coupon.setEndDate(request.endDate());
    }

    private DiscountCoupon findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new DiscountCouponNotFoundException("No existe el cupón con id " + id));
    }

    private DiscountCouponResponse toResponse(DiscountCoupon coupon) {
        return new DiscountCouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getDescription(),
                coupon.getType(),
                coupon.getValue(),
                coupon.getMinSubtotal(),
                coupon.getRequiredEmailDomain(),
                coupon.isFirstPurchaseOnly(),
                coupon.isActive(),
                coupon.getStartDate(),
                coupon.getEndDate(),
                coupon.getCreatedAt()
        );
    }
}
