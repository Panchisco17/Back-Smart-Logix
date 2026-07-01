package com.smartlogix.payment.service;

import com.smartlogix.payment.client.OrderClient;
import com.smartlogix.payment.client.OrderLineSummary;
import com.smartlogix.payment.client.OrderSummaryResponse;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private static final Locale CHILE_LOCALE =
            new Locale.Builder().setLanguage("es").setRegion("CL").build();

    private final OrderClient orderClient;

    @Value("${app.gateway.base-url}")
    private String gatewayBaseUrl;

    @Value("${app.frontend.success-url}")
    private String frontendSuccessUrl;

    @Value("${app.frontend.failure-url}")
    private String frontendFailureUrl;

    @Value("${app.frontend.pending-url}")
    private String frontendPendingUrl;

    public PaymentService(OrderClient orderClient) {
        this.orderClient = orderClient;
    }

    public String buildCheckoutPage(String orderNumber) {
        OrderSummaryResponse order = orderClient.getOrder(orderNumber);

        String confirmBaseUrl = gatewayBaseUrl + "/api/payments/" + orderNumber + "/confirm";

        StringBuilder itemsHtml = new StringBuilder();
        for (OrderLineSummary line : order.lines()) {
            String displayName = (line.productName() != null && !line.productName().isBlank())
                    ? line.productName()
                    : line.sku();
            itemsHtml.append("<div class=\"mini-item\">")
                    .append("<span>").append(displayName).append(" &times;").append(line.quantity()).append("</span>")
                    .append("<span>$").append(formatClp(line.unitPrice())).append("</span>")
                    .append("</div>");
        }

        return """
                <html>
                <head>
                <title>Pasarela de pago (simulada)</title>
                <style>
                    * { box-sizing: border-box; }
                    body {
                        font-family: 'Segoe UI', Arial, sans-serif;
                        background: #eef0f4;
                        min-height: 100vh; margin: 0; padding: 48px 16px;
                    }
                    .wrapper { max-width: 760px; margin: 0 auto; }
                    .logo-block { margin-bottom: 22px; }
                    .logo-webpay { font-size: 30px; font-weight: 800; color: #5c1a56; letter-spacing: -0.5px; }
                    .logo-webpay .dot { color: #e4007c; }
                    .logo-transbank { font-size: 12px; font-weight: 700; color: #e4007c; letter-spacing: 0.3px; margin-top: -4px; }
                    .card { background: #fff; border-radius: 14px; box-shadow: 0 10px 35px rgba(30,20,40,0.12); padding: 40px; display: flex; gap: 40px; flex-wrap: wrap; }
                    .col { flex: 1; min-width: 260px; }
                    .eyebrow { font-size: 12px; color: #6b6b7d; margin: 0 0 4px; }
                    .merchant { font-size: 20px; font-weight: 800; color: #2563eb; margin: 0 0 18px; }
                    .mini-items { margin-bottom: 6px; }
                    .mini-item { display: flex; justify-content: space-between; font-size: 12px; color: #6b6b7d; padding: 3px 0; }
                    .amount-label { font-size: 12px; color: #6b6b7d; margin: 10px 0 2px; }
                    .amount { font-size: 26px; font-weight: 800; color: #1a1a2e; margin: 0 0 22px; }
                    .method-title { font-size: 13px; color: #333; font-weight: 600; margin-bottom: 10px; }
                    .method { display: flex; align-items: center; gap: 12px; border: 1.5px solid #e4e4ee; border-radius: 10px; padding: 12px 14px; margin-bottom: 10px; }
                    .method.selected { border-color: #8a2387; box-shadow: 0 0 0 3px rgba(138,35,135,0.08); }
                    .method.disabled { opacity: 0.5; }
                    .method .icon { font-size: 20px; width: 26px; text-align: center; }
                    .method .title { font-size: 14px; font-weight: 700; color: #1a1a2e; }
                    .method .subtitle { font-size: 11px; color: #8a8a99; }
                    .cancel-link { display: inline-block; margin-top: 6px; color: #2563eb; font-size: 13px; text-decoration: none; }
                    .cancel-link:hover { text-decoration: underline; }

                    .card-preview {
                        background: linear-gradient(135deg, #3a3a55, #23233a);
                        border-radius: 12px; padding: 16px 18px; color: #fff; margin-bottom: 18px;
                        box-shadow: 0 8px 20px rgba(30,20,40,0.25);
                    }
                    .card-preview .chip { font-size: 20px; margin-bottom: 18px; }
                    .card-preview .preview-number { font-size: 17px; letter-spacing: 2px; font-family: 'Courier New', monospace; margin-bottom: 14px; }
                    .card-preview .preview-bottom { display: flex; justify-content: space-between; align-items: center; font-size: 12px; opacity: 0.85; }

                    label { display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.4px; color: #6b6b85; margin: 12px 0 5px; }
                    .input-wrap { position: relative; }
                    input { width: 100%%; padding: 11px 12px; border: 1.5px solid #dcdcec; border-radius: 8px; font-size: 14px; transition: border-color 0.15s; background: #fbfbfe; }
                    input:focus { outline: none; border-color: #8a2387; background: #fff; }
                    .input-icon { position: absolute; right: 12px; top: 50%%; transform: translateY(-50%%); font-size: 18px; }
                    .row { display: flex; gap: 12px; }
                    .row > div { flex: 1; }
                    button[type=submit] {
                        width: 100%%; margin-top: 20px; padding: 13px; border: none; border-radius: 8px;
                        font-size: 15px; font-weight: 700; cursor: pointer; color: #fff;
                        background: #5c1a56;
                        display: flex; align-items: center; justify-content: center; gap: 8px;
                        transition: background 0.15s;
                    }
                    button[type=submit]:hover { background: #4a1546; }
                    button[type=submit]:disabled { background: #9ca3af; cursor: not-allowed; }
                    .spinner { display: none; width: 15px; height: 15px; border-radius: 50%%; border: 2px solid rgba(255,255,255,0.5); border-top-color: #fff; animation: spin 0.7s linear infinite; }
                    @keyframes spin { to { transform: rotate(360deg); } }
                    .error { color: #dc2626; background: #fef2f2; border: 1px solid #fecaca; padding: 8px 10px; border-radius: 8px; font-size: 12px; margin-top: 10px; display: none; }
                    .redcompra { display: flex; justify-content: center; margin-top: 16px; }
                    .redcompra img { height: 26px; width: auto; }
                    .footer-note { text-align: center; font-size: 11.5px; color: #6b6b7d; margin-top: 22px; line-height: 1.5; }
                    .footer-note .policy { color: #2563eb; font-weight: 600; }
                    .hint { font-size: 11px; color: #8a8a99; margin-top: 14px; line-height: 1.5; }
                </style>
                </head>
                <body>
                    <div class="wrapper">
                        <div class="logo-block">
                            <div class="logo-webpay">webpay<span class="dot">.</span></div>
                            <div class="logo-transbank">transbank<span class="dot">.</span></div>
                        </div>
                        <div class="card">
                            <div class="col">
                                <p class="eyebrow">Estás pagando en:</p>
                                <p class="merchant">Smart Logix</p>

                                <div class="mini-items">
                                    %s
                                </div>

                                <p class="amount-label">Monto a pagar:</p>
                                <p class="amount">$%s</p>

                                <p class="method-title">Selecciona tu medio de pago:</p>
                                <div class="method selected">
                                    <span class="icon">💳</span>
                                    <div>
                                        <div class="title">Tarjetas</div>
                                        <div class="subtitle">Débito, Crédito, Prepago</div>
                                    </div>
                                </div>
                                <div class="method disabled">
                                    <span class="icon">📱</span>
                                    <div>
                                        <div class="title">Billeteras digitales</div>
                                        <div class="subtitle">Próximamente</div>
                                    </div>
                                </div>

                                <a class="cancel-link" href="%s?approved=false">Anular compra y volver</a>
                            </div>

                            <div class="col">
                                <p class="method-title">Ingresa los datos de tu tarjeta:</p>

                                <div class="card-preview">
                                    <div class="chip">🏦</div>
                                    <div class="preview-number" id="preview-number">•••• •••• •••• ••••</div>
                                    <div class="preview-bottom">
                                        <span id="preview-name">NOMBRE DEL TITULAR</span>
                                        <span id="preview-expiry">MM/AA</span>
                                    </div>
                                </div>

                                <form id="payment-form" novalidate>
                                    <label>Nombre del titular</label>
                                    <input id="cardHolder" placeholder="Como aparece en la tarjeta" autocomplete="cc-name" />

                                    <label>Número de tarjeta</label>
                                    <div class="input-wrap">
                                        <input id="cardNumber" placeholder="XXXX XXXX XXXX XXXX" inputmode="numeric" autocomplete="cc-number" maxlength="19" />
                                        <span class="input-icon" id="card-brand">💳</span>
                                    </div>

                                    <div class="row">
                                        <div>
                                            <label>Vencimiento</label>
                                            <input id="expiry" placeholder="MM/AA" inputmode="numeric" autocomplete="cc-exp" maxlength="5" />
                                        </div>
                                        <div>
                                            <label>CVV</label>
                                            <input id="cvv" placeholder="123" inputmode="numeric" autocomplete="cc-csc" maxlength="3" type="password" />
                                        </div>
                                    </div>

                                    <p id="form-error" class="error"></p>
                                    <button type="submit" id="pay-btn">
                                        <span class="spinner" id="pay-spinner"></span>
                                        <span id="pay-label">Continuar</span>
                                    </button>
                                </form>

                                <div class="redcompra">
                                    <img src="/assets/redcompra-logo.jpg" alt="RedCompra" />
                                </div>

                                <p class="hint">Simulación educativa: cualquier tarjeta con datos válidos aprueba el pago. Una tarjeta que comience con "4000" simula un rechazo (fondos insuficientes).</p>
                            </div>
                        </div>
                        <p class="footer-note">🔒 Transacción respaldada por Transbank. Revisa las <span class="policy">Políticas de Seguridad y Privacidad</span>.</p>
                    </div>

                    <script>
                        var form = document.getElementById('payment-form');
                        var errorEl = document.getElementById('form-error');
                        var payBtn = document.getElementById('pay-btn');
                        var paySpinner = document.getElementById('pay-spinner');
                        var payLabel = document.getElementById('pay-label');
                        var brandEl = document.getElementById('card-brand');
                        var previewNumber = document.getElementById('preview-number');
                        var previewName = document.getElementById('preview-name');
                        var previewExpiry = document.getElementById('preview-expiry');
                        var confirmUrl = '%s';

                        function updatePreviewNumber(digits) {
                            var groups = [];
                            for (var i = 0; i < 4; i++) {
                                var part = digits.slice(i * 4, i * 4 + 4);
                                groups.push(part ? part.padEnd(4, '•') : '••••');
                            }
                            previewNumber.textContent = groups.join(' ');
                        }

                        document.getElementById('cardHolder').addEventListener('input', function (e) {
                            previewName.textContent = e.target.value.trim() ? e.target.value.toUpperCase() : 'NOMBRE DEL TITULAR';
                        });

                        document.getElementById('cardNumber').addEventListener('input', function (e) {
                            var digits = e.target.value.replace(/\\D/g, '').slice(0, 16);
                            e.target.value = digits.replace(/(.{4})/g, '$1 ').trim();
                            updatePreviewNumber(digits);

                            if (/^4/.test(digits)) { brandEl.textContent = '💳 VISA'; }
                            else if (/^5/.test(digits)) { brandEl.textContent = '💳 MC'; }
                            else if (/^3/.test(digits)) { brandEl.textContent = '💳 AMEX'; }
                            else { brandEl.textContent = '💳'; }
                        });

                        document.getElementById('expiry').addEventListener('input', function (e) {
                            var digits = e.target.value.replace(/\\D/g, '').slice(0, 4);
                            e.target.value = digits.length > 2 ? digits.slice(0, 2) + '/' + digits.slice(2) : digits;
                            previewExpiry.textContent = e.target.value || 'MM/AA';
                        });

                        document.getElementById('cvv').addEventListener('input', function (e) {
                            e.target.value = e.target.value.replace(/\\D/g, '').slice(0, 3);
                        });

                        function showError(message) {
                            errorEl.textContent = message;
                            errorEl.style.display = 'block';
                        }

                        form.addEventListener('submit', function (event) {
                            event.preventDefault();
                            errorEl.style.display = 'none';

                            var holder = document.getElementById('cardHolder').value.trim();
                            var cardNumber = document.getElementById('cardNumber').value.replace(/\\s/g, '');
                            var expiry = document.getElementById('expiry').value;
                            var cvv = document.getElementById('cvv').value;

                            if (!holder) { return showError('Ingresa el nombre del titular.'); }
                            if (!/^[0-9]{16}$/.test(cardNumber)) { return showError('El número de tarjeta debe tener 16 dígitos.'); }

                            var expiryMatch = /^(0[1-9]|1[0-2])\\/([0-9]{2})$/.exec(expiry);
                            if (!expiryMatch) { return showError('Vencimiento inválido. Usa el formato MM/AA.'); }

                            var expDate = new Date(2000 + parseInt(expiryMatch[2], 10), parseInt(expiryMatch[1], 10), 0);
                            if (expDate < new Date()) { return showError('La tarjeta está vencida.'); }

                            if (!/^[0-9]{3}$/.test(cvv)) { return showError('El CVV debe tener 3 dígitos.'); }

                            payBtn.disabled = true;
                            paySpinner.style.display = 'inline-block';
                            payLabel.textContent = 'Procesando pago...';

                            var approved = cardNumber.indexOf('4000') !== 0;

                            setTimeout(function () {
                                window.location.href = confirmUrl + '?approved=' + approved;
                            }, 1400);
                        });
                    </script>
                </body>
                </html>
                """.formatted(
                        itemsHtml, formatClp(order.totalAmount()),
                        confirmBaseUrl,
                        confirmBaseUrl);
    }

    public String confirmPayment(String orderNumber, boolean approved) {
        OrderSummaryResponse result = orderClient.confirmPayment(orderNumber, approved);
        return switch (result.status()) {
            case "PAID" -> frontendSuccessUrl;
            case "FAILED" -> frontendFailureUrl;
            default -> frontendPendingUrl;
        };
    }

    private String formatClp(BigDecimal amount) {
        return NumberFormat.getIntegerInstance(CHILE_LOCALE).format(amount);
    }
}
