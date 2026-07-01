package com.smartlogix.order.repository;

import com.smartlogix.order.domain.DiscountCoupon;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscountCouponRepository extends JpaRepository<DiscountCoupon, Long> {

    Optional<DiscountCoupon> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
