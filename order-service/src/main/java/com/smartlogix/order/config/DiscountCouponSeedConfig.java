package com.smartlogix.order.config;

import com.smartlogix.order.domain.DiscountCoupon;
import com.smartlogix.order.domain.DiscountType;
import com.smartlogix.order.repository.DiscountCouponRepository;
import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DiscountCouponSeedConfig {

    @Bean
    CommandLineRunner discountCouponSeeder(DiscountCouponRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }

            repository.save(build("2X1", "Lleva 2 o más y paga el más barato gratis",
                    DiscountType.TWO_FOR_ONE, null, null, null, false));

            repository.save(build("DUOC25", "25% de descuento en la primera compra para correos @duocuc.cl",
                    DiscountType.PERCENTAGE, new BigDecimal("25"), null, "@duocuc.cl", true));

            repository.save(build("SMART5000", "$5.000 de descuento en carritos de $20.000 o más",
                    DiscountType.FIXED_AMOUNT, new BigDecimal("5000"), new BigDecimal("20000"), null, false));
        };
    }

    private DiscountCoupon build(String code, String description, DiscountType type, BigDecimal amount,
            BigDecimal minSubtotal, String requiredEmailDomain, boolean firstPurchaseOnly) {
        DiscountCoupon coupon = new DiscountCoupon();
        coupon.setCode(code);
        coupon.setDescription(description);
        coupon.setType(type);
        coupon.setAmount(amount);
        coupon.setMinSubtotal(minSubtotal);
        coupon.setRequiredEmailDomain(requiredEmailDomain);
        coupon.setFirstPurchaseOnly(firstPurchaseOnly);
        coupon.setActive(true);
        return coupon;
    }
}
