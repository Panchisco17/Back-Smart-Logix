package com.smartlogix.payment.controller;

import com.smartlogix.payment.service.PaymentService;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping(value = "/{orderNumber}/checkout", produces = MediaType.TEXT_HTML_VALUE)
    public String checkoutPage(@PathVariable String orderNumber) {
        return paymentService.buildCheckoutPage(orderNumber);
    }

    @GetMapping("/{orderNumber}/confirm")
    public ResponseEntity<Void> confirmPayment(
            @PathVariable String orderNumber,
            @RequestParam boolean approved) {
        String redirectUrl = paymentService.confirmPayment(orderNumber, approved);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }
}
