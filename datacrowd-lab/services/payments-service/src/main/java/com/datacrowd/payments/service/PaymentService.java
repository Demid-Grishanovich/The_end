package com.datacrowd.payments.service;

import com.datacrowd.payments.dto.CheckoutRequest;
import com.datacrowd.payments.dto.CheckoutResponse;
import com.datacrowd.payments.entity.PaymentEntity;
import com.datacrowd.payments.entity.PaymentStatus;
import com.datacrowd.payments.repo.PaymentRepository;
import com.datacrowd.payments.security.CurrentUser;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final CoreBillingClient coreBillingClient;

    private final boolean stripeEnabled;
    private final String stripeSecretKey;
    private final String stripeWebhookSecret;

    private final int defaultAmountCents;
    private final int defaultTaskQuota;

    private final String successUrl;
    private final String cancelUrl;

    public PaymentService(
            PaymentRepository paymentRepository,
            CoreBillingClient coreBillingClient,
            @Value("${app.stripe.enabled:false}") boolean stripeEnabled,
            @Value("${app.stripe.secret-key:}") String stripeSecretKey,
            @Value("${app.stripe.webhook-secret:}") String stripeWebhookSecret,
            @Value("${app.payments.default-amount-cents:500}") int defaultAmountCents,
            @Value("${app.payments.default-task-quota:100}") int defaultTaskQuota,
            @Value("${app.payments.success-url:http://localhost:8080/api/payments/checkout/success}") String successUrl,
            @Value("${app.payments.cancel-url:http://localhost:8080/api/payments/checkout/cancel}") String cancelUrl
    ) {
        this.paymentRepository = paymentRepository;
        this.coreBillingClient = coreBillingClient;
        this.stripeEnabled = stripeEnabled;
        this.stripeSecretKey = stripeSecretKey;
        this.stripeWebhookSecret = stripeWebhookSecret;
        this.defaultAmountCents = defaultAmountCents;
        this.defaultTaskQuota = defaultTaskQuota;
        this.successUrl = successUrl;
        this.cancelUrl = cancelUrl;

        if (stripeEnabled) {
            if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
                throw new IllegalStateException("Stripe is enabled but app.stripe.secret-key is empty");
            }
            if (stripeWebhookSecret == null || stripeWebhookSecret.isBlank()) {
                throw new IllegalStateException("Stripe is enabled but app.stripe.webhook-secret is empty");
            }
        }
    }

    @Transactional
    public CheckoutResponse createCheckout(CheckoutRequest req, CurrentUser user) {
        int amount = (req.getAmountCents() != null) ? req.getAmountCents() : defaultAmountCents;
        int quota = (req.getTaskQuota() != null) ? req.getTaskQuota() : defaultTaskQuota;

        PaymentEntity payment = new PaymentEntity();
        payment.setProjectId(req.getProjectId());
        payment.setUserId(parseUuidOrNull(user.userId()));
        payment.setAmountCents(amount);
        payment.setCurrency("EUR");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setTaskQuota(quota);

        payment = paymentRepository.save(payment);

        // Если Stripe выключен — возвращаем "mock url" (для диплома / локального теста)
        if (!stripeEnabled) {
            String mockUrl = "http://localhost:8080/api/payments/mock/pay/" + payment.getId();
            return new CheckoutResponse(payment.getId(), mockUrl);
        }

        Stripe.apiKey = stripeSecretKey;

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("eur")
                                                .setUnitAmount((long) amount)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("DataCrowd Lab – Project quota purchase")
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .putMetadata("paymentId", payment.getId().toString())
                .putMetadata("projectId", payment.getProjectId().toString())
                .putMetadata("userId", payment.getUserId() != null ? payment.getUserId().toString() : "")
                .putMetadata("taskQuota", String.valueOf(quota))
                .build();

        try {
            Session session = Session.create(params);
            payment.setStripeSessionId(session.getId());
            paymentRepository.save(payment);

            return new CheckoutResponse(payment.getId(), session.getUrl());
        } catch (Exception e) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            throw new IllegalArgumentException("Failed to create Stripe checkout session: " + e.getMessage());
        }
    }

    public Event verifyWebhook(String payload, String sigHeader) throws SignatureVerificationException {
        if (!stripeEnabled) {
            // Если Stripe выключен — webhook нам не нужен, но возвращать ошибку не будем.
            return null;
        }
        return Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret);
    }

    @Transactional
    public void handleStripeEvent(Event event) {
        if (event == null) return;

        if (!"checkout.session.completed".equals(event.getType())) {
            return; // MVP: обрабатываем только успешное завершение checkout
        }

        Session session = (Session) event.getDataObjectDeserializer()
                .getObject()
                .orElse(null);

        if (session == null) return;

        String sessionId = session.getId();

        PaymentEntity payment = paymentRepository.findByStripeSessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for stripe_session_id=" + sessionId));

        // Идемпотентность: если уже SUCCEEDED — ничего не делаем
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return;
        }

        payment.setStatus(PaymentStatus.SUCCEEDED);

        if (session.getPaymentIntent() != null) {
            payment.setStripePaymentIntentId(session.getPaymentIntent());
        }

        paymentRepository.save(payment);

        // Главное действие блока 8: выдать доступ в core-service (billing_status=PAID + quota)
        coreBillingClient.grantPaidAccess(payment.getProjectId(), payment.getTaskQuota());
    }

    @Transactional
    public void markPaidMock(UUID paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return; // идемпотентность
        }

        payment.setStatus(PaymentStatus.SUCCEEDED);
        paymentRepository.save(payment);

        coreBillingClient.grantPaidAccess(payment.getProjectId(), payment.getTaskQuota());
    }


    private UUID parseUuidOrNull(String s) {
        try {
            if (s == null || s.isBlank()) return null;
            return UUID.fromString(s);
        } catch (Exception e) {
            return null;
        }
    }
}
