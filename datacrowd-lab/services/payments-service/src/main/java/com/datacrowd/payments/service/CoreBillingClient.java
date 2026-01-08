package com.datacrowd.payments.service;

import com.datacrowd.payments.dto.BillingGrantRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class CoreBillingClient {

    private final RestClient restClient;
    private final String internalToken;

    public CoreBillingClient(
            @Value("${app.core.internal-base-url}") String baseUrl,
            @Value("${app.security.internal-token}") String internalToken
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.internalToken = internalToken;
    }

    public void grantPaidAccess(UUID projectId, int taskQuotaDelta) {
        BillingGrantRequest body = new BillingGrantRequest(taskQuotaDelta, "PAID");

        restClient.post()
                .uri("/internal/billing/projects/{projectId}/grant", projectId)
                .header("X-Internal-Token", internalToken)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
