package com.datacrowd.payments.api;

import com.datacrowd.payments.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/webhooks")
public class StripeWebhookController {

    private final PaymentService paymentService;

    public StripeWebhookController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping(value = "/stripe", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> stripeWebhook(HttpServletRequest request, @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader)
            throws IOException {

        String payload;
        try (BufferedReader reader = request.getReader()) {
            payload = reader.lines().collect(Collectors.joining("\n"));
        }

        try {
            Event event = paymentService.verifyWebhook(payload, sigHeader);
            paymentService.handleStripeEvent(event);
            return Map.of("status", "ok");
        } catch (SignatureVerificationException e) {
            return Map.of("status", "invalid_signature");
        } catch (Exception e) {
            // Stripe требует 2xx, иначе будет ретраить; для диплома так безопаснее.
            return Map.of("status", "error", "message", e.getMessage());
        }
    }
}
