package com.datacrowd.payments.service;

import com.datacrowd.payments.dto.CheckoutRequest;
import com.datacrowd.payments.entity.PaymentEntity;
import com.datacrowd.payments.entity.PaymentStatus;
import com.datacrowd.payments.repo.PaymentRepository;
import com.datacrowd.payments.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PaymentServiceTest {

    PaymentRepository paymentRepository;
    CoreBillingClient coreBillingClient;
    PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        coreBillingClient = mock(CoreBillingClient.class);

        paymentService = new PaymentService(
                paymentRepository,
                coreBillingClient,
                false, // stripeEnabled
                "",    // secret key
                "",    // webhook secret
                500,   // default amount
                100,   // default quota
                "http://localhost:8080/api/payments/checkout/success",
                "http://localhost:8080/api/payments/checkout/cancel"
        );

        // For createCheckout we need repository.save(..) to return an entity with ID
        when(paymentRepository.save(any(PaymentEntity.class))).thenAnswer(inv -> {
            PaymentEntity e = inv.getArgument(0);
            if (e.getId() == null) {
                ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
            }
            return e;
        });
    }

    @Test
    void createCheckout_whenStripeDisabled_returnsMockUrl_andSavesPayment() {
        UUID projectId = UUID.randomUUID();
        CheckoutRequest req = new CheckoutRequest();
        req.setProjectId(projectId);
        req.setAmountCents(null); // use default
        req.setTaskQuota(null);   // use default

        CurrentUser user = new CurrentUser(UUID.randomUUID().toString(), "u", "CLIENT");

        var resp = paymentService.createCheckout(req, user);

        assertThat(resp).isNotNull();
        assertThat(resp.getPaymentId()).isNotNull();
        assertThat(resp.getCheckoutUrl()).contains("/api/payments/mock/pay/" + resp.getPaymentId());

        ArgumentCaptor<PaymentEntity> captor = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(paymentRepository).save(captor.capture());

        PaymentEntity saved = captor.getValue();
        assertThat(saved.getProjectId()).isEqualTo(projectId);
        assertThat(saved.getUserId()).isNotNull();
        assertThat(saved.getAmountCents()).isEqualTo(500);
        assertThat(saved.getTaskQuota()).isEqualTo(100);
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void createCheckout_whenUserIdNotUuid_savesNullUserId() {
        UUID projectId = UUID.randomUUID();
        CheckoutRequest req = new CheckoutRequest();
        req.setProjectId(projectId);

        CurrentUser user = new CurrentUser("not-a-uuid", "u", "CLIENT");

        paymentService.createCheckout(req, user);

        ArgumentCaptor<PaymentEntity> captor = ArgumentCaptor.forClass(PaymentEntity.class);
        verify(paymentRepository).save(captor.capture());

        assertThat(captor.getValue().getUserId()).isNull();
    }

    @Test
    void markPaidMock_setsSucceeded_saves_andGrantsAccess() {
        UUID paymentId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        PaymentEntity p = new PaymentEntity();
        ReflectionTestUtils.setField(p, "id", paymentId);
        p.setProjectId(projectId);
        p.setTaskQuota(123);
        p.setStatus(PaymentStatus.PENDING);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(p));

        paymentService.markPaidMock(paymentId);

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        verify(paymentRepository).save(p);
        verify(coreBillingClient).grantPaidAccess(projectId, 123);
    }

    @Test
    void markPaidMock_whenAlreadySucceeded_isIdempotent() {
        UUID paymentId = UUID.randomUUID();

        PaymentEntity p = new PaymentEntity();
        ReflectionTestUtils.setField(p, "id", paymentId);
        p.setProjectId(UUID.randomUUID());
        p.setTaskQuota(50);
        p.setStatus(PaymentStatus.SUCCEEDED);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(p));

        paymentService.markPaidMock(paymentId);

        verify(paymentRepository, never()).save(any());
        verify(coreBillingClient, never()).grantPaidAccess(any(), anyInt());
    }
}
