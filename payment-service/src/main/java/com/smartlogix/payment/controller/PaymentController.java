package com.smartlogix.payment.controller;

import com.smartlogix.payment.service.PaymentService;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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
        return paymentService.buildCheckoutRedirect(orderNumber);
    }

    // Transbank redirige de vuelta aquí. Según la versión de su API puede ser
    // GET o POST, y "token_ws" puede venir como query param o como body
    // form-urlencoded, por eso se aceptan ambos métodos.
    @RequestMapping(value = "/{orderNumber}/return", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Void> handleReturn(
            @PathVariable String orderNumber,
            @RequestParam(value = "token_ws", required = false) String tokenWs) {
        String redirectUrl = paymentService.confirmReturn(orderNumber, tokenWs);
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
    }
}
