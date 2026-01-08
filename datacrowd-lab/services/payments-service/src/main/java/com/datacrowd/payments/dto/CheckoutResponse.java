package com.datacrowd.payments.dto;

import java.util.UUID;

public class CheckoutResponse {
    private UUID paymentId;
    private String checkoutUrl;

    public CheckoutResponse(UUID paymentId, String checkoutUrl) {
        this.paymentId = paymentId;
        this.checkoutUrl = checkoutUrl;
    }

    public UUID getPaymentId() { return paymentId; }
    public String getCheckoutUrl() { return checkoutUrl; }
}
