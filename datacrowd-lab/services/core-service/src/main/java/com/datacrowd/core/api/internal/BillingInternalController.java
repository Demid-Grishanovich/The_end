package com.datacrowd.core.api.internal;

import com.datacrowd.core.dto.billing.BillingGrantRequest;
import com.datacrowd.core.service.billing.BillingService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/billing")
public class BillingInternalController {

    private final BillingService billingService;

    public BillingInternalController(BillingService billingService) {
        this.billingService = billingService;
    }

    @PostMapping("/projects/{projectId}/grant")
    public Map<String, String> grant(@PathVariable UUID projectId, @RequestBody BillingGrantRequest req) {
        billingService.grantPaidAccess(projectId, req.getTaskQuotaDelta(), req.getBillingStatus());
        return Map.of("status", "ok");
    }
}
