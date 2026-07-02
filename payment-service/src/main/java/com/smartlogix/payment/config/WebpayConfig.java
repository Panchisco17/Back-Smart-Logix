package com.smartlogix.payment.config;

import cl.transbank.common.IntegrationType;
import cl.transbank.webpay.common.WebpayOptions;
import cl.transbank.webpay.webpayplus.WebpayPlus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebpayConfig {

    @Bean
    public WebpayPlus.Transaction webpayTransaction(
            @Value("${transbank.webpay.commerce-code}") String commerceCode,
            @Value("${transbank.webpay.api-key}") String apiKey,
            @Value("${transbank.webpay.environment}") String environment) {
        WebpayOptions options = new WebpayOptions(
                commerceCode, apiKey, IntegrationType.valueOf(environment.toUpperCase()));
        return new WebpayPlus.Transaction(options);
    }
}
