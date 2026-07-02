package com.smartlogix.payment.service;

import cl.transbank.webpay.exception.TransactionCommitException;
import cl.transbank.webpay.exception.TransactionCreateException;
import cl.transbank.webpay.webpayplus.WebpayPlus;
import cl.transbank.webpay.webpayplus.responses.WebpayPlusTransactionCommitResponse;
import cl.transbank.webpay.webpayplus.responses.WebpayPlusTransactionCreateResponse;
import com.smartlogix.payment.client.OrderClient;
import com.smartlogix.payment.client.OrderSummaryResponse;
import com.smartlogix.payment.exception.PaymentGatewayException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final Locale CHILE_LOCALE =
            new Locale.Builder().setLanguage("es").setRegion("CL").build();

    private final OrderClient orderClient;
    private final WebpayPlus.Transaction webpayTransaction;

    @Value("${app.gateway.base-url}")
    private String gatewayBaseUrl;

    @Value("${app.frontend.success-url}")
    private String frontendSuccessUrl;

    @Value("${app.frontend.failure-url}")
    private String frontendFailureUrl;

    @Value("${app.frontend.pending-url}")
    private String frontendPendingUrl;

    public PaymentService(OrderClient orderClient, WebpayPlus.Transaction webpayTransaction) {
        this.orderClient = orderClient;
        this.webpayTransaction = webpayTransaction;
    }

    // Inicia una transacción real en Webpay Plus y devuelve una página que
    // redirige automáticamente al formulario de pago de Transbank (requiere
    // un POST con el token, no un simple redirect HTTP).
    public String buildCheckoutRedirect(String orderNumber) {
        OrderSummaryResponse order = orderClient.getOrder(orderNumber);
        String returnUrl = gatewayBaseUrl + "/api/payments/" + orderNumber + "/return";

        BigDecimal amount = order.totalAmount().setScale(0, RoundingMode.HALF_UP);

        WebpayPlusTransactionCreateResponse createResponse;
        try {
            createResponse = webpayTransaction.create(
                    orderNumber, "session-" + orderNumber, amount.doubleValue(), returnUrl);
        } catch (IOException | TransactionCreateException ex) {
            throw new PaymentGatewayException(
                    "No fue posible iniciar el pago en Webpay para la orden " + orderNumber, ex);
        }

        return buildRedirectForm(createResponse.getUrl(), createResponse.getToken(), order);
    }

    // Aplica el resultado real que entrega Transbank al volver del formulario
    // de pago. Si no llega token_ws, el cliente anuló el pago o expiró el
    // tiempo del formulario en Webpay (Transbank indica no llamar a commit
    // en ese caso) y se trata como rechazado.
    public String confirmReturn(String orderNumber, String tokenWs) {
        boolean approved;

        if (tokenWs != null && !tokenWs.isBlank()) {
            try {
                WebpayPlusTransactionCommitResponse commitResponse = webpayTransaction.commit(tokenWs);
                approved = "AUTHORIZED".equals(commitResponse.getStatus()) && commitResponse.getResponseCode() == 0;
            } catch (IOException | TransactionCommitException ex) {
                log.error("No fue posible confirmar la transacción Webpay de la orden {}", orderNumber, ex);
                approved = false;
            }
        } else {
            log.info("Orden {}: el cliente anuló el pago o expiró el formulario de Webpay.", orderNumber);
            approved = false;
        }

        OrderSummaryResponse result = orderClient.confirmPayment(orderNumber, approved);
        return switch (result.status()) {
            case "PAID" -> frontendSuccessUrl;
            case "FAILED" -> frontendFailureUrl;
            default -> frontendPendingUrl;
        };
    }

    private String buildRedirectForm(String webpayUrl, String token, OrderSummaryResponse order) {
        return """
                <html>
                <head>
                <title>Redirigiendo a Webpay...</title>
                <style>
                    body {
                        font-family: 'Segoe UI', Arial, sans-serif;
                        background: #eef0f4;
                        min-height: 100vh; margin: 0; display: flex; align-items: center; justify-content: center;
                    }
                    .card {
                        background: #fff; border-radius: 14px; box-shadow: 0 10px 35px rgba(30,20,40,0.12);
                        padding: 40px; text-align: center; max-width: 380px;
                    }
                    .logo-webpay { font-size: 26px; font-weight: 800; color: #5c1a56; letter-spacing: -0.5px; }
                    .logo-webpay .dot { color: #e4007c; }
                    .spinner {
                        width: 28px; height: 28px; border-radius: 50%%; margin: 20px auto;
                        border: 3px solid #e4e4ee; border-top-color: #5c1a56; animation: spin 0.8s linear infinite;
                    }
                    @keyframes spin { to { transform: rotate(360deg); } }
                    p { color: #6b6b7d; font-size: 13px; }
                    .amount { font-size: 20px; font-weight: 800; color: #1a1a2e; margin: 10px 0; }
                    button {
                        margin-top: 10px; padding: 10px 20px; border: none; border-radius: 8px;
                        background: #5c1a56; color: #fff; font-weight: 700; cursor: pointer;
                    }
                </style>
                </head>
                <body>
                    <div class="card">
                        <div class="logo-webpay">webpay<span class="dot">.</span></div>
                        <div class="spinner"></div>
                        <p>Redirigiendo al pago seguro de Webpay para la orden <strong>%s</strong></p>
                        <p class="amount">$%s</p>
                        <form id="webpay-form" method="POST" action="%s">
                            <input type="hidden" name="token_ws" value="%s" />
                            <button type="submit">Continuar</button>
                        </form>
                    </div>
                    <script>
                        document.getElementById('webpay-form').submit();
                    </script>
                </body>
                </html>
                """.formatted(order.orderNumber(), formatClp(order.totalAmount()), webpayUrl, token);
    }

    private String formatClp(BigDecimal amount) {
        return NumberFormat.getIntegerInstance(CHILE_LOCALE).format(amount);
    }
}
