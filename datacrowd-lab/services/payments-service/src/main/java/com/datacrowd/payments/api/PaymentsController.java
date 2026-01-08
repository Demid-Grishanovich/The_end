package com.datacrowd.payments.api;

import com.datacrowd.payments.dto.CheckoutRequest;
import com.datacrowd.payments.dto.CheckoutResponse;
import com.datacrowd.payments.security.CurrentUser;
import com.datacrowd.payments.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/payments")
public class PaymentsController {

    private final PaymentService paymentService;

    public PaymentsController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }

    @PostMapping("/checkout")
    public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest request,
                                     @AuthenticationPrincipal CurrentUser user) {
        return paymentService.createCheckout(request, user);
    }

    // удобные заглушки для success/cancel (чтобы не было 404 если откроешь URL)
    @GetMapping("/checkout/success")
    public Map<String, String> success() {
        return Map.of("status", "success");
    }

    @GetMapping("/checkout/cancel")
    public Map<String, String> cancel() {
        return Map.of("status", "cancel");
    }
    /**
     * MOCK endpoint: имитация успешной оплаты без Stripe.
     * Используется когда STRIPE_ENABLED=false.
     */
    @PostMapping("/mock/pay/{paymentId}")
    public Map<String, String> mockPay(@PathVariable java.util.UUID paymentId) {
        paymentService.markPaidMock(paymentId);
        return Map.of("status", "paid");
    }
}
